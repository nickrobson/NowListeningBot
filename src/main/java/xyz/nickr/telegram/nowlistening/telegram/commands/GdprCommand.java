package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.jtelegram.api.util.TextBuilder;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.NLModel;
import xyz.nickr.telegram.nowlistening.db.models.NowListeningMessage;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;
import xyz.nickr.telegram.nowlistening.telegram.TelegramController;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static xyz.nickr.telegram.nowlistening.telegram.commands.CommandUtil.reply;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class GdprCommand {
    private enum Mode {MAIN, BOT_INFO, SEE_DATA, CLEAR_DATA, CLEAR_DATA_ARE_YOU_SURE, DO_CLEAR_DATA, DATA_CLEARED}

    private enum ClearingData {EVERYTHING, MESSAGES, USER_DATA, PLAYING_DATA}

    private final DatabaseController databaseController;
    private final TelegramController telegramController;

    public void onCommand(TelegramBot bot, Message message) {
        try {
            SendResponse sendResponse = bot.execute(
                    reply(message, getMenuMessage(message.from().id(), Mode.MAIN, null))
                            .parseMode(ParseMode.HTML)
                            .disableWebPagePreview(true)
                            .replyMarkup(getKeyboardLayout(Mode.MAIN, null))
            );
            if (!sendResponse.isOk()) {
                bot.execute(reply(message, TextBuilder.create().escaped("An error occurred. Please contact @nickrobson.")));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            bot.execute(reply(message, TextBuilder.create().escaped("An error occurred. Please contact @nickrobson.")));
        }
    }


    public TextBuilder getMenuMessage(long telegramUserId, Mode mode, ClearingData clearingData) {
        TextBuilder builder = TextBuilder.create();

        if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE && clearingData == null) {
            mode = Mode.CLEAR_DATA;
            System.err.println("Mode was 'Are you sure?' but user hadn't clicked anything!");
        }

        switch (mode) {
            case MAIN -> builder.escaped("This is the Main Menu. Here you can choose to see data the bot has collected on you, or see information about the bot.");
            case BOT_INFO -> builder.escaped("""
                    This bot lets you share what music you listen to on Spotify with other people on Telegram.
                    You can choose to have the bot automatically keep the message updated or avoid this.
                    This bot is intended to be used through inline-mode.
                    That is, you should write \"""").code("@" + telegramController.getBotUsername() + " ").escaped("""
                    " in the chat box to access it in most cases.

                    For data exports and GDPR compliance, use the /gdpr command.

                    This bot was created by @nickrobson, who you can chat with if you have any additional questions.""");
            case SEE_DATA -> {
                builder.escaped("Here you can see the data on you that this bot has collected.");
                try {
                    Set<NowListeningMessage> messageSet = databaseController.getNowListeningMessages(telegramUserId);
                    if (!messageSet.isEmpty()) {
                        builder.newLine().newLine().escaped("Messages:");
                        builder.newLine().code(
                                "[" +
                                        messageSet.stream()
                                                .map(NLModel::toJSON)
                                                .collect(Collectors.joining(",\n"))
                                        + "]");
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    builder.escaped("An error occurred while retrieving the messages from the database.");
                }
                try {
                    Optional<SpotifyPlayingData> playingData = databaseController.getPlayingData(telegramUserId);
                    if (playingData.isPresent()) {
                        builder.newLine().newLine().escaped("Playing Data:");
                        builder.newLine().code(playingData.get().toJSON());
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    builder.escaped("An error occurred while retrieving the playing data from the database.");
                }
                try {
                    Optional<SpotifyUser> spotifyUser = databaseController.getSpotifyUser(telegramUserId);
                    if (spotifyUser.isPresent()) {
                        builder.newLine().newLine().escaped("Spotify Data:");
                        builder.newLine().code(spotifyUser.get().toJSON());
                        builder.newLine().italics("Note: I also store an access and refresh token to access the Spotify API. These are hidden here as they are sensitive.");
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    builder.escaped("An error occurred while retrieving the spotify data from the database.");
                }
            }
            case CLEAR_DATA -> {
                builder.escaped("Here you can delete the data on you that this bot has collected.");
                if (clearingData != null) {
                    builder.newLine().newLine();
                    switch (clearingData) {
                        case EVERYTHING -> builder.escaped("All data deleted.");
                        case MESSAGES -> builder.escaped("All messages deleted.");
                        case PLAYING_DATA -> builder.escaped("All playing data deleted.");
                        case USER_DATA -> builder.escaped("All user data deleted.");
                    }
                    if (clearingData != ClearingData.EVERYTHING) {
                        builder.newLine().escaped("You can see what other data exists from the See My Data menu.");
                    }
                }
            }
            case CLEAR_DATA_ARE_YOU_SURE -> {
                builder.escaped("Are you sure you want to delete ");
                switch (clearingData) {
                    case MESSAGES -> builder.italics("ALL messages");
                    case PLAYING_DATA -> builder.italics("ALL playing data");
                    case USER_DATA -> builder.italics("ALL spotify data");
                    case EVERYTHING -> builder.italics("everything");
                }
                builder.escaped("?").newLine()
                        .escaped("This is irreversible.").newLine()
                        .escaped("You will not get your data back if you click \"Yes, I'm sure.\"");
            }
            case DATA_CLEARED -> {
                switch (clearingData) {
                    case MESSAGES -> builder.italics("All message data has been deleted, you'll need to create new messages if you want them to update.");
                    case PLAYING_DATA -> builder.italics("All playing data has been deleted. Please note, if you've still granted access to this bot in your Spotify account, it will auto-refresh.");
                    case USER_DATA -> builder.italics("All spotify data has been deleted. You'll need to run /start if you want to use this bot again.");
                    case EVERYTHING -> builder.italics("All data has been deleted. You'll need to run /start if you want to use this bot again.");
                }
            }
        }

        return builder;
    }

    public InlineKeyboardMarkup getKeyboardLayout(Mode mode, ClearingData clearingData) {
        if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE && clearingData == null) {
            mode = Mode.CLEAR_DATA;
            System.err.println("Mode was 'Are you sure?' but user hadn't clicked anything!");
        }

        InlineKeyboardButton backToMainMenu = new InlineKeyboardButton("← Back to Main Menu")
                .callbackData("gdpr/" + Mode.MAIN);
        if (mode == Mode.MAIN) {
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton("See my data...")
                            .callbackData("gdpr/" + Mode.SEE_DATA),
                    new InlineKeyboardButton("Bot Information...")
                            .callbackData("gdpr/" + Mode.BOT_INFO)
            );
        } else if (mode == Mode.BOT_INFO) {
            return new InlineKeyboardMarkup(backToMainMenu);
        } else if (mode == Mode.SEE_DATA) {
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton("Clear some/all data...")
                            .callbackData("gdpr/" + Mode.CLEAR_DATA),
                    backToMainMenu
            );
        } else if (mode == Mode.CLEAR_DATA) {
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Delete EVERYTHING (everything below)")
                                    .callbackData("gdpr/" + Mode.CLEAR_DATA_ARE_YOU_SURE + "/" + ClearingData.EVERYTHING),
                    },
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Delete Messages\n(messages to keep updated with current music)")
                                    .callbackData("gdpr/" + Mode.CLEAR_DATA_ARE_YOU_SURE + "/" + ClearingData.MESSAGES),
                    },
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Delete Playing Data\n(last played track's name, artist, and URL)")
                                    .callbackData("gdpr/" + Mode.CLEAR_DATA_ARE_YOU_SURE + "/" + ClearingData.PLAYING_DATA),
                    },
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Delete Spotify Data\n(authentication tokens)")
                                    .callbackData("gdpr/" + Mode.CLEAR_DATA_ARE_YOU_SURE + "/" + ClearingData.USER_DATA),
                    },
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("← Back to See My Data")
                                    .callbackData("gdpr/" + Mode.SEE_DATA)
                    }
            );
        } else if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE) {
            return new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Yes, I'm sure.")
                                    .callbackData("gdpr/" + Mode.DO_CLEAR_DATA + "/" + clearingData)
                    },
                    new InlineKeyboardButton[]{
                            new InlineKeyboardButton("Cancel")
                                    .callbackData("gdpr/" + Mode.CLEAR_DATA)
                    }
            );
        } else if (mode == Mode.DATA_CLEARED) {
            return new InlineKeyboardMarkup(backToMainMenu);
        } else {
            throw new IllegalStateException("UNEXPECTED MENU MODE: is " + mode.name());
        }
    }

    public void handleButtonClick(long telegramUserId, Message message, String callbackData) {
        try {
            String[] parts = callbackData.split(Pattern.quote("/"));
            String gdprPart = parts.length > 0 ? parts[0] : null;
            Mode mode = parts.length > 1 ? Mode.valueOf(parts[1]) : null;
            ClearingData clearingData = parts.length > 2 ? ClearingData.valueOf(parts[2]) : null;
            if (!"gdpr".equals(gdprPart))
                return;
            if (mode == null)
                return;

            if (mode == Mode.DO_CLEAR_DATA) {
                // gdpr/DO_CLEAR_DATA/clearing_data
                if (clearingData == null)
                    return;
                switch (clearingData) {
                    case MESSAGES -> this.databaseController.deleteAllMessages(telegramUserId);
                    case USER_DATA -> this.databaseController.deleteSpotifyUser(telegramUserId);
                    case PLAYING_DATA -> this.databaseController.deletePlayingData(telegramUserId);
                    case EVERYTHING -> {
                        this.databaseController.deleteAllMessages(telegramUserId);
                        this.databaseController.deleteSpotifyUser(telegramUserId);
                        this.databaseController.deletePlayingData(telegramUserId);
                    }
                    default -> System.err.println("UNEXPECTED CLEARING DATA MODE: is " + clearingData.name());
                }
                mode = Mode.DATA_CLEARED;
            }

            telegramController.getBot().execute(
                    new EditMessageText(message.chat().id(), message.messageId(), getMenuMessage(telegramUserId, mode, clearingData).toHtml())
                            .parseMode(ParseMode.HTML)
                            .disableWebPagePreview(true)
                            .replyMarkup(getKeyboardLayout(mode, clearingData))
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
