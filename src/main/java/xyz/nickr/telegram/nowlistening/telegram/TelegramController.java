package xyz.nickr.telegram.nowlistening.telegram;

import com.google.gson.JsonObject;
import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.commands.filters.MentionFilter;
import com.jtelegram.api.events.inline.ChosenInlineResultEvent;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.inline.keyboard.InlineKeyboardButton;
import com.jtelegram.api.inline.keyboard.InlineKeyboardMarkup;
import com.jtelegram.api.inline.keyboard.InlineKeyboardRow;
import com.jtelegram.api.requests.message.edit.EditTextMessage;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.update.PollingUpdateProvider;
import com.jtelegram.api.util.TextBuilder;
import java.net.URI;
import java.sql.SQLException;
import java.util.Set;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.NowListeningMessage;
import xyz.nickr.telegram.nowlistening.db.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
public class TelegramController {

    public static final String AUTH_WITH_SPOTIFY_ID = "AuthSpotify";
    public static final String NOW_LISTENING_MSG_ID = "NowListeningMsg";

    private final String apiKey;
    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    private volatile TelegramBot bot;

    public TelegramController(JsonObject config, DatabaseController databaseController, SpotifyController spotifyController) {
        JsonObject tg = config.getAsJsonObject("telegram");

        this.apiKey = tg.getAsJsonPrimitive("api_key").getAsString();
        this.databaseController = databaseController;
        this.spotifyController = spotifyController;
    }

    public void start() {
        TelegramBotRegistry registry = TelegramBotRegistry.builder()
                .updateProvider(new PollingUpdateProvider())
                .build();

        registry.registerBot(apiKey, (bot, err) -> {
            if (err != null) {
                throw new RuntimeException("Failed to login to Telegram", err);
            }
            this.bot = bot;
            this.bot.getEventRegistry().registerEvent(InlineQueryEvent.class, new InlineQueryHandler(bot, databaseController, spotifyController, this));
            this.bot.getEventRegistry().registerEvent(ChosenInlineResultEvent.class, new ChosenInlineResultHandler(databaseController, this));
            this.bot.getCommandRegistry().registerCommand("start", new MentionFilter((event, command) -> {
                try {
                    if (databaseController.getSpotifyUser(command.getSender().getId()).isPresent()) {
                        this.bot.perform(SendText.builder()
                                .chatId(command.getChat().getChatId())
                                .replyToMessageID(command.getBaseMessage().getMessageId())
                                .text("You are already authenticated with Spotify.")
                                .errorHandler(Throwable::printStackTrace)
                                .build());
                    } else {
                        URI uri = spotifyController.getAuthorizationUri(command.getSender().getId());
                        this.bot.perform(SendText.builder()
                                .chatId(command.getChat().getChatId())
                                .replyToMessageID(command.getBaseMessage().getMessageId())
                                .text("To authenticate with Spotify, please visit this link:\n" + uri.toString())
                                .errorHandler(Throwable::printStackTrace)
                                .build());
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    this.bot.perform(SendText.builder()
                            .chatId(command.getChat().getChatId())
                            .replyToMessageID(command.getBaseMessage().getMessageId())
                            .text("An error occurred. Please contact @nickrobson if it keeps happening.")
                            .errorHandler(Throwable::printStackTrace)
                            .build());
                }
                return true;
            }));

            this.spotifyController.addListener(playingData -> updateNowListeningMessages(playingData.getTelegramUserId()));
        });
    }

    public void updateNowListeningMessages(long telegramUserId) throws SQLException {
        SpotifyPlayingData playingData = databaseController.getPlayingData(telegramUserId).orElse(null);

        EditTextMessage.EditTextMessageBuilder messageBuilder = EditTextMessage.builder()
                .text(getMessage(playingData))
                .replyMarkup(getKeyboard(playingData))
                .disableWebPagePreview(true);

        Set<NowListeningMessage> messageSet = databaseController.getNowListeningMessages(telegramUserId);
        for (NowListeningMessage message : messageSet) {
            try {
                this.bot.perform(messageBuilder
                        .inlineMessageId(message.getInlineMessageId())
                        .errorHandler(err -> {
                            if (err != null && err.getDescription() != null) {
                                if ("MESSAGE_ID_INVALID".contains(err.getDescription())) {
                                    try {
                                        databaseController.deleteNowListeningMessage(message);
                                    } catch (SQLException ex) {
                                        ex.printStackTrace();
                                    }
                                } else if (!err.getDescription().contains("message is not modified")) {
                                    err.printStackTrace();
                                }
                            } else if (err != null) {
                                err.printStackTrace();
                            }
                        })
                        .build());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public TextBuilder getMessage(SpotifyPlayingData track) {
        if (track == null) {
            return TextBuilder.create()
                    .escaped("I'm not listening to Spotify right now \uD83D\uDD07");
        } else if (!track.isPlaying()) {
            return TextBuilder.create()
                    .escaped("I'm not listening to Spotify right now \uD83D\uDD07")
                    .newLine()
                    .escaped("\uD83C\uDFB5 I was last listening to ")
                    .bold(track.getLastTrackName())
                    .escaped(" by ")
                    .italics(track.getLastTrackArtist())
                    .escaped(" \uD83C\uDFB5");
        } else {
            return TextBuilder.create()
                    .escaped("\uD83C\uDFB5 I'm listening to ")
                    .bold(track.getLastTrackName())
                    .escaped(" by ")
                    .italics(track.getLastTrackArtist())
                    .escaped(" \uD83C\uDFB5");
        }
    }

    public InlineKeyboardMarkup getKeyboard(SpotifyPlayingData track) {
        InlineKeyboardRow.InlineKeyboardRowBuilder rowBuilder = InlineKeyboardRow.builder();

        if (track != null) {
            rowBuilder.button(InlineKeyboardButton.builder()
                    .label("Open in Spotify")
                    .url(track.getLastTrackUrl())
                    .build());
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rowBuilder
                        .button(InlineKeyboardButton.builder()
                                .label("Share your music!")
                                .switchInlineQuery("")
                                .build())
                        .build())
                .build();
    }

}
