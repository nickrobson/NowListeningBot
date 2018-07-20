package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.commands.Command;
import com.jtelegram.api.commands.CommandHandler;
import com.jtelegram.api.events.message.TextMessageEvent;
import com.jtelegram.api.ex.TelegramException;
import com.jtelegram.api.menu.Menu;
import com.jtelegram.api.menu.MenuHandler;
import com.jtelegram.api.menu.MenuRow;
import com.jtelegram.api.menu.SimpleMenuButton;
import com.jtelegram.api.menu.viewer.RegularMenuViewer;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.util.TextBuilder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.NowListeningMessage;
import xyz.nickr.telegram.nowlistening.db.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.SpotifyUser;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class GdprCommand implements CommandHandler {

    private final DatabaseController databaseController;

    @Override
    public void onCommand(TextMessageEvent event, Command command) {
        event.getBot().perform(SendText.builder()
                .chatId(event.getMessage().getChat().getChatId())
                .replyToMessageID(event.getMessage().getMessageId())
                .text(TextBuilder.create().italics("Loading..."))
                .callback(message -> {
                    GdprMenu menu = new GdprMenu(event.getBot(), command.getSender().getId(), databaseController);
                    menu.addViewer(RegularMenuViewer.builder()
                            .chatId(message.getChat().getChatId())
                            .messageId(message.getMessageId())
                            .build());
                    MenuHandler.registerMenu(menu);
                })
                .errorHandler(ex -> {
                    ex.printStackTrace();
                    event.getBot().perform(SendText.builder()
                            .chatId(event.getMessage().getChat().getChatId())
                            .text(TextBuilder.create().escaped("An error occurred. Please contact @nickrobson."))
                            .build());
                })
                .build());
    }

    private static class GdprMenu extends Menu {

        private enum Mode { MAIN, BOT_INFO, SEE_DATA, CLEAR_DATA, CLEAR_DATA_ARE_YOU_SURE }
        private enum ClearingData { EVERYTHING, MESSAGES, USER_DATA, PLAYING_DATA }

        private final long telegramUser;
        private final DatabaseController databaseController;

        private Mode mode = Mode.MAIN;
        private ClearingData clearingData = null;
        private boolean error = false;

        protected GdprMenu(TelegramBot bot, long telegramUser, DatabaseController databaseController) {
            super(bot);
            this.telegramUser = telegramUser;
            this.databaseController = databaseController;
        }

        @Override
        public TextBuilder getMenuMessage() {
            TextBuilder builder = TextBuilder.create();

            if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE && clearingData == null) {
                mode = Mode.CLEAR_DATA;
                error = true;
                System.err.println("Mode was 'Are you sure?' but user hadn't clicked anything!");
            }

            if (error) {
                builder.bold("An error occurred. Please contact @nickrobson.").newLine().newLine();
                error = false;
            }

            switch (mode) {
                case MAIN:
                    builder.escaped("This is the Main Menu. Here you can choose to see data the bot has collected on you, or see information about the bot.");
                    break;
                case BOT_INFO:
                    builder.escaped("This bot aims to let people on Telegram see the music you listen to.\n" +
                            "You can choose to have the bot automatically keep the message updated or avoid this.\n" +
                            "This bot is intended to be used through inline-mode.\n" +
                            "That is, you should write \"").code("@NowListeningBot ").escaped("\" in the chat-box to access it in most cases.\n" +
                            "For things relating to GDPR-compliance, you may use /gdpr.\n\n" +
                            "This bot was created by @nickrobson, whom you may contact for any additional questions.");
                    break;
                case SEE_DATA:
                    builder.escaped("Here you can see the data on you that this bot has collected.");
                    try {
                        Set<NowListeningMessage> messageSet = databaseController.getNowListeningMessages(telegramUser);
                        if (!messageSet.isEmpty()) {
                            builder.newLine().newLine().escaped("Messages:");
                            messageSet.forEach(m -> builder.newLine().escaped("- " + m.toString()));
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        builder.escaped("An error occurred while retrieving the messages from the database.");
                    }
                    try {
                        Optional<SpotifyPlayingData> playingData = databaseController.getPlayingData(telegramUser);
                        if (playingData.isPresent()) {
                            builder.newLine().newLine().escaped("Playing Data:");
                            builder.newLine().escaped(playingData.get().toString());
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        builder.escaped("An error occurred while retrieving the playing data from the database.");
                    }
                    try {
                        Optional<SpotifyUser> spotifyUser = databaseController.getSpotifyUser(telegramUser);
                        if (spotifyUser.isPresent()) {
                            builder.newLine().newLine().escaped("Spotify Data:");
                            builder.newLine().escaped(spotifyUser.get().toString());
                            builder.newLine().italics("Note: I also store an access and refresh token to access the Spotify API. These are hidden here as they are sensitive.");
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                        builder.escaped("An error occurred while retrieving the spotify data from the database.");
                    }
                    break;
                case CLEAR_DATA:
                    builder.escaped("Here you can delete the data on you that this bot has collected.");
                    if (clearingData != null) {
                        builder.newLine().newLine();
                        switch (clearingData) {
                            case EVERYTHING:
                                builder.escaped("All data deleted.");
                                break;
                            case MESSAGES:
                                builder.escaped("All messages deleted.");
                                break;
                            case PLAYING_DATA:
                                builder.escaped("All playing data deleted.");
                                break;
                            case USER_DATA:
                                builder.escaped("All user data deleted.");
                                break;
                        }
                        if (clearingData != ClearingData.EVERYTHING) {
                            builder.newLine().escaped("You can see what other data exists from the See My Data menu.");
                        }
                        clearingData = null;
                    }
                    break;
                case CLEAR_DATA_ARE_YOU_SURE:
                    builder.escaped("Are you sure you want to delete ");
                    switch (clearingData) {
                        case MESSAGES:
                            builder.italics("ALL messages");
                            break;
                        case PLAYING_DATA:
                            builder.italics("ALL playing data");
                            break;
                        case USER_DATA:
                            builder.italics("ALL spotify data");
                            break;
                        case EVERYTHING:
                            builder.italics("everything");
                            break;
                    }
                    builder.escaped("?").newLine()
                            .escaped("This is irreversible.").newLine()
                            .escaped("You will not get your data back if you click \"Yes, I'm sure.\"");
                    break;
            }

            return builder;
        }

        @Override
        public List<MenuRow> getRows() {
            List<MenuRow> rowList = new ArrayList<>(4);

            if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE && clearingData == null) {
                mode = Mode.CLEAR_DATA;
                System.err.println("Mode was 'Are you sure?' but user hadn't clicked anything!");
            }

            if (mode == Mode.MAIN) {
                rowList.add(MenuRow.from(
                        SimpleMenuButton.builder()
                                .label("See my data...")
                                .onPress((button, event) -> {
                                    this.mode = Mode.SEE_DATA;
                                    return true;
                                })
                                .build(),
                        SimpleMenuButton.builder()
                                .label("Bot Information...")
                                .onPress((button, event) -> {
                                    this.mode = Mode.BOT_INFO;
                                    return true;
                                })
                                .build()
                ));
            } else if (mode == Mode.BOT_INFO) {
                rowList.add(MenuRow.from(SimpleMenuButton.builder()
                        .label("← Back to Main Menu")
                        .onPress((button, event) -> {
                            this.mode = Mode.MAIN;
                            return true;
                        })
                        .build()));
            } else if (mode == Mode.SEE_DATA) {
                rowList.addAll(Arrays.asList(
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Clear some/all data...")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA;
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("← Back to Main Menu")
                                .onPress((button, event) -> {
                                    this.mode = Mode.MAIN;
                                    return true;
                                })
                                .build())
                ));
            } else if (mode == Mode.CLEAR_DATA) {
                rowList.addAll(Arrays.asList(
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Delete EVERYTHING (everything below)")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA_ARE_YOU_SURE;
                                    this.clearingData = ClearingData.EVERYTHING;
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Delete Messages\n(messages to keep updated with current music)")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA_ARE_YOU_SURE;
                                    this.clearingData = ClearingData.MESSAGES;
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Delete Playing Data\n(last played track's name, artist, and URL)")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA_ARE_YOU_SURE;
                                    this.clearingData = ClearingData.PLAYING_DATA;
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Delete Spotify Data\n(authentication tokens)")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA_ARE_YOU_SURE;
                                    this.clearingData = ClearingData.USER_DATA;
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("← Back to See My Data")
                                .onPress((button, event) -> {
                                    this.mode = Mode.SEE_DATA;
                                    return true;
                                })
                                .build())
                ));
            } else if (mode == Mode.CLEAR_DATA_ARE_YOU_SURE) {
                rowList.addAll(Arrays.asList(
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Yes, I'm sure.")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA;
                                    try {
                                        long telegramUser = event.getQuery().getFrom().getId();
                                        switch (clearingData) {
                                            case MESSAGES:
                                                this.databaseController.deleteAllMessages(telegramUser);
                                                break;
                                            case USER_DATA:
                                                this.databaseController.deleteSpotifyUser(telegramUser);
                                                break;
                                            case PLAYING_DATA:
                                                this.databaseController.deletePlayingData(telegramUser);
                                                break;
                                            case EVERYTHING:
                                                this.databaseController.deleteAllMessages(telegramUser);
                                                this.databaseController.deleteSpotifyUser(telegramUser);
                                                this.databaseController.deletePlayingData(telegramUser);
                                                break;
                                            default:
                                                System.err.println("UNEXPECTED CLEARING DATA MODE: is " + clearingData.name());
                                                break;
                                        }
                                    } catch (SQLException ex) {
                                        ex.printStackTrace();
                                        this.error = true;
                                    }
                                    return true;
                                })
                                .build()),
                        MenuRow.from(SimpleMenuButton.builder()
                                .label("Cancel")
                                .onPress((button, event) -> {
                                    this.mode = Mode.CLEAR_DATA;
                                    return true;
                                })
                                .build())
                ));
            } else {
                throw new IllegalStateException("UNEXPECTED MENU MODE: is " + mode.name());
            }

            return rowList;
        }

        @Override
        public void handleException(TelegramException exception) {
            exception.printStackTrace();
        }
    }

}
