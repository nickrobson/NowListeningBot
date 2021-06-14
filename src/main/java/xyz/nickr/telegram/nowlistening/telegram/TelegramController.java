package xyz.nickr.telegram.nowlistening.telegram;

import com.google.gson.JsonObject;
import com.jtelegram.api.util.TextBuilder;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetMeResponse;
import lombok.Getter;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.NowListeningMessage;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.telegram.commands.GdprCommand;
import xyz.nickr.telegram.nowlistening.telegram.commands.StartCommand;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Nick Robson
 */
public class TelegramController {

    public static final String AUTH_WITH_SPOTIFY_ID = "AuthSpotify";
    public static final String NOW_LISTENING_MSG_UPDATE_FOREVER_ID = "NowListeningUpdateForever";
    public static final String NOW_LISTENING_MSG_UPDATE_ONE_DAY_ID = "NowListeningUpdateADay";
    public static final String NOW_LISTENING_MSG_NO_UPDATE_ID = "NowListeningNoUpdate";
    public static final String CONTINUE_GETTING_UPDATES = "ContinueGettingUpdates";

    private final String apiKey;
    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    @Getter
    private volatile TelegramBot bot;
    @Getter
    private volatile String botUsername;

    public TelegramController(JsonObject config, DatabaseController databaseController, SpotifyController spotifyController) {
        JsonObject tg = config.getAsJsonObject("telegram");

        this.apiKey = tg.getAsJsonPrimitive("api_key").getAsString();
        this.databaseController = databaseController;
        this.spotifyController = spotifyController;
    }

    public void start(Runnable onReady) {
        this.bot = new TelegramBot(apiKey);

        try {
            GetMeResponse response = this.bot.execute(new GetMe());
            if (!response.isOk()) {
                throw new RuntimeException("Failed to login to Telegram with error code: " + response.errorCode());
            }
            this.botUsername = response.user().username();
            System.out.format("[NowListening] Logged in as @%s\n", this.botUsername);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to login to Telegram", ex);
        }

        StartCommand startCommand = new StartCommand(databaseController, spotifyController);
        GdprCommand gdprCommand = new GdprCommand(databaseController, this);

        InlineQueryHandler inlineQueryHandler = new InlineQueryHandler(databaseController, spotifyController, this);
        ChosenInlineResultHandler chosenInlineResultHandler = new ChosenInlineResultHandler(databaseController, this);
        CallbackQueryHandler callbackQueryHandler = new CallbackQueryHandler(databaseController, this, gdprCommand);

        this.bot.setUpdatesListener(updates -> {
            try {
                for (Update update : updates) {
                    if (update.inlineQuery() != null) {
                        inlineQueryHandler.onInlineQuery(bot, update.inlineQuery());
                    }
                    if (update.chosenInlineResult() != null) {
                        chosenInlineResultHandler.onChosenInlineResult(update.chosenInlineResult());
                    }
                    if (update.callbackQuery() != null) {
                        callbackQueryHandler.onCallbackQuery(update.callbackQuery());
                    }
                    if (update.message() != null && update.message().text() != null) {
                        Message message = update.message();
                        if (message.chat().type() != Chat.Type.Private)
                            continue;
                        String text = message.text();
                        if (!text.startsWith("/"))
                            continue;
                        String[] commandWords = text.split("\s+");
                        String[] commandParts = commandWords[0].split("@", 2);
                        String command = commandParts[0].toLowerCase(Locale.ENGLISH);
                        if ("/start".equals(command)) {
                            startCommand.onCommand(bot, message);
                        } else if ("/gdpr".equals(command)) {
                            gdprCommand.onCommand(bot, message);
                        } else {
                            bot.execute(new SendMessage(message.chat().id(), "Unknown command"));
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, Throwable::printStackTrace);

        this.spotifyController.addListener(playingData -> updateEnabledNowListeningMessages(playingData.getTelegramUserId()));

        onReady.run();
    }

    public void updateMessage(NowListeningMessage message) throws SQLException {
        SpotifyPlayingData playingData = databaseController.getPlayingData(message.getTelegramUserId()).orElse(null);
        this.bot.execute(
                new EditMessageText(message.getInlineMessageId(), getMessage(playingData).toHtml())
                        .parseMode(ParseMode.HTML)
                        .replyMarkup(getKeyboard(playingData))
                        .disableWebPagePreview(true)
        );
    }

    public void updateEnabledNowListeningMessages(long telegramUserId) throws SQLException {
        SpotifyPlayingData playingData = databaseController.getPlayingData(telegramUserId).orElse(null);

        Set<NowListeningMessage> messageSet = databaseController.getEnabledNowListeningMessages(telegramUserId);
        for (NowListeningMessage message : messageSet) {
            try {
                bot.execute(
                        new EditMessageText(message.getInlineMessageId(), getMessage(playingData).toHtml())
                                .parseMode(ParseMode.HTML)
                                .replyMarkup(getKeyboard(playingData))
                                .disableWebPagePreview(true)
                );
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public int updateDisabledNowListeningMessages() throws SQLException {
        Set<NowListeningMessage> messageSet = databaseController.getEnabledMessagesToBeDisabled();
        for (NowListeningMessage message : messageSet) {
            try {
                long telegramUserId = message.getTelegramUserId();
                SpotifyPlayingData playingData = databaseController.getPlayingData(telegramUserId).orElse(null);

                bot.execute(
                        new EditMessageText(message.getInlineMessageId(), getMessage(playingData, false).toHtml())
                                .replyMarkup(getKeyboard(playingData, false))
                                .disableWebPagePreview(true)
                );
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        databaseController.disableMessages(messageSet);
        return messageSet.size();
    }

    private Consumer<TelegramException> onError(NowListeningMessage message) {
        return err -> {
            if (err != null) {
                if (err.response().description() != null) {
                    if (err.response().description().contains("MESSAGE_ID_INVALID")) {
                        try {
                            databaseController.deleteNowListeningMessage(message);
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    } else if (err.response().description().contains("message is not modified")) {
                        // we just suppress these...
                    } else {
                        err.printStackTrace();
                    }
                } else {
                    err.printStackTrace();
                }
            }
        };
    }

    public TextBuilder getMessage(SpotifyPlayingData track) {
        return getMessage(track, true);
    }

    public TextBuilder getMessage(SpotifyPlayingData track, boolean enabled) {
        TextBuilder builder;
        if (track == null) {
            builder = TextBuilder.create()
                    .escaped("I'm not listening to Spotify right now \uD83D\uDD07");
        } else if (!track.isPlaying()) {
            builder = TextBuilder.create()
                    .escaped("I'm not listening to Spotify right now \uD83D\uDD07")
                    .newLine()
                    .escaped("\uD83C\uDFB5 I was last listening to ")
                    .bold(track.getLastTrackName())
                    .escaped(" by ")
                    .italics(track.getLastTrackArtist())
                    .escaped(" \uD83C\uDFB5");
        } else {
            builder = TextBuilder.create()
                    .escaped("\uD83C\uDFB5 I'm listening to ")
                    .bold(track.getLastTrackName())
                    .escaped(" by ")
                    .italics(track.getLastTrackArtist())
                    .escaped(" \uD83C\uDFB5");
        }

        if (!enabled) {
            builder.newLine().newLine()
                    .italics("This message has stopped updating.");
        }

        return builder;
    }

    public InlineKeyboardMarkup getKeyboard(SpotifyPlayingData track) {
        return getKeyboard(track, true);
    }

    public InlineKeyboardMarkup getKeyboard(SpotifyPlayingData track, boolean enabled) {
        List<InlineKeyboardButton> mainRow = new ArrayList<>();
        if (track != null) {
            mainRow.add(
                    new InlineKeyboardButton("Open in Spotify").url(track.getLastTrackUrl())
            );
        }

        mainRow.add(
                new InlineKeyboardButton("Share your music!").switchInlineQuery("")
        );

        if (enabled) {
            return new InlineKeyboardMarkup(
                    mainRow.toArray(new InlineKeyboardButton[0])
            );
        }

        return new InlineKeyboardMarkup(
                mainRow.toArray(new InlineKeyboardButton[0]),
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Continue getting updates").callbackData(CONTINUE_GETTING_UPDATES)
                }
        );
    }
}
