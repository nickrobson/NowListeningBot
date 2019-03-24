package xyz.nickr.telegram.nowlistening.telegram;

import com.google.gson.JsonObject;
import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.chat.ChatType;
import com.jtelegram.api.commands.filters.ChatTypeFilter;
import com.jtelegram.api.commands.filters.MentionFilter;
import com.jtelegram.api.commands.filters.TextFilter;
import com.jtelegram.api.events.inline.ChosenInlineResultEvent;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.events.inline.keyboard.CallbackQueryEvent;
import com.jtelegram.api.ex.TelegramException;
import com.jtelegram.api.inline.keyboard.InlineKeyboardButton;
import com.jtelegram.api.inline.keyboard.InlineKeyboardMarkup;
import com.jtelegram.api.inline.keyboard.InlineKeyboardRow;
import com.jtelegram.api.menu.events.UnregisteredMenuInteractionEvent;
import com.jtelegram.api.requests.message.edit.EditTextMessage;
import com.jtelegram.api.update.PollingUpdateProvider;
import com.jtelegram.api.util.TextBuilder;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.NowListeningMessage;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.telegram.commands.BroadcastCommand;
import xyz.nickr.telegram.nowlistening.telegram.commands.GdprCommand;
import xyz.nickr.telegram.nowlistening.telegram.commands.StartCommand;

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

    public TelegramController(JsonObject config, DatabaseController databaseController, SpotifyController spotifyController) {
        JsonObject tg = config.getAsJsonObject("telegram");

        this.apiKey = tg.getAsJsonPrimitive("api_key").getAsString();
        this.databaseController = databaseController;
        this.spotifyController = spotifyController;
    }

    public void start(Runnable onReady) {
        TelegramBotRegistry registry = TelegramBotRegistry.builder()
                .updateProvider(new PollingUpdateProvider())
                .build();

        registry.registerBot(apiKey, (bot, err) -> {
            if (err != null) {
                throw new RuntimeException("Failed to login to Telegram", err);
            }
            System.out.format("[NowListening] Logged in as @%s\n", bot.getBotInfo().getUsername());

            this.bot = bot;
            this.bot.getEventRegistry().registerEvent(InlineQueryEvent.class, new InlineQueryHandler(databaseController, spotifyController, this));
            this.bot.getEventRegistry().registerEvent(ChosenInlineResultEvent.class, new ChosenInlineResultHandler(databaseController, this));
            this.bot.getEventRegistry().registerEvent(CallbackQueryEvent.class, new CallbackQueryHandler(databaseController, this));
            this.bot.getEventRegistry().registerEvent(UnregisteredMenuInteractionEvent.class, e -> System.out.println(e.toString()));
            this.bot.getCommandRegistry().registerCommand(new MentionFilter(
                    new ChatTypeFilter(ChatType.PRIVATE,
                            new TextFilter("start", false, new StartCommand(databaseController, spotifyController)),
                            new TextFilter("gdpr", false, new GdprCommand(databaseController)),
                            new TextFilter("broadcast", false, new BroadcastCommand(databaseController))
            )));

            this.spotifyController.addListener(playingData -> updateEnabledNowListeningMessages(playingData.getTelegramUserId()));

            onReady.run();
        });
    }

    public void updateMessage(NowListeningMessage message) throws SQLException {
        SpotifyPlayingData playingData = databaseController.getPlayingData(message.getTelegramUserId()).orElse(null);
        this.bot.perform(EditTextMessage.builder()
                .text(getMessage(playingData))
                .replyMarkup(getKeyboard(playingData))
                .disableWebPagePreview(true)
                .inlineMessageId(message.getInlineMessageId())
                .errorHandler(onError(message))
                .build());
    }

    public void updateEnabledNowListeningMessages(long telegramUserId) throws SQLException {
        SpotifyPlayingData playingData = databaseController.getPlayingData(telegramUserId).orElse(null);

        EditTextMessage.EditTextMessageBuilder messageBuilder = EditTextMessage.builder()
                .text(getMessage(playingData))
                .replyMarkup(getKeyboard(playingData))
                .disableWebPagePreview(true);

        Set<NowListeningMessage> messageSet = databaseController.getEnabledNowListeningMessages(telegramUserId);
        for (NowListeningMessage message : messageSet) {
            try {
                perform(messageBuilder, message);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public int updateDisabledNowListeningMessages() throws SQLException {
        Set<NowListeningMessage> messageSet = databaseController.getEnabledMessagesToBeDisabled();
        Map<Long, EditTextMessage.EditTextMessageBuilder> messageBuilderMap = new HashMap<>();
        for (NowListeningMessage message : messageSet) {
            try {
                long telegramUserId = message.getTelegramUserId();
                EditTextMessage.EditTextMessageBuilder messageBuilder = messageBuilderMap.get(telegramUserId);
                if (messageBuilder == null) {
                    SpotifyPlayingData playingData = databaseController.getPlayingData(telegramUserId).orElse(null);

                    messageBuilder = EditTextMessage.builder()
                            .text(getMessage(playingData, false))
                            .replyMarkup(getKeyboard(playingData, false))
                            .disableWebPagePreview(true);

                    messageBuilderMap.put(telegramUserId, messageBuilder);
                }
                perform(messageBuilder, message);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        databaseController.disableMessages(messageSet);
        return messageSet.size();
    }

    private void perform(EditTextMessage.EditTextMessageBuilder messageBuilder, NowListeningMessage message) {
        this.bot.perform(messageBuilder
                .inlineMessageId(message.getInlineMessageId())
                .errorHandler(onError(message))
                .build());
    }

    private Consumer<TelegramException> onError(NowListeningMessage message) {
        return err -> {
            if (err != null) {
                if (err.getDescription() != null) {
                    if (err.getDescription().contains("MESSAGE_ID_INVALID")) {
                        try {
                            databaseController.deleteNowListeningMessage(message);
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    } else if (err.getDescription().contains("message is not modified")) {
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
        InlineKeyboardRow.InlineKeyboardRowBuilder rowBuilder = InlineKeyboardRow.builder();

        if (track != null) {
            rowBuilder.button(InlineKeyboardButton.builder()
                    .label("Open in Spotify")
                    .url(track.getLastTrackUrl())
                    .build());
        }

        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder =
                InlineKeyboardMarkup.builder()
                        .keyboard(rowBuilder
                                .button(InlineKeyboardButton.builder()
                                        .label("Share your music!")
                                        .switchInlineQuery("")
                                        .build())
                                .build());

        if (!enabled) {
            builder.keyboard(InlineKeyboardRow.builder()
                    .button(InlineKeyboardButton.builder()
                            .label("Continue getting updates")
                            .callbackData(CONTINUE_GETTING_UPDATES)
                            .build())
                    .build());
        }

        return builder.build();
    }
}
