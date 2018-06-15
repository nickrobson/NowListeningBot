package xyz.nickr.telegram.nowlistening.telegram;

import com.google.gson.JsonObject;
import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.commands.filters.MentionFilter;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.update.PollingUpdateProvider;
import java.net.URI;
import java.sql.SQLException;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
public class TelegramController {

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
            this.bot.getEventRegistry().registerEvent(InlineQueryEvent.class, new InlineQueryHandler(bot, databaseController, spotifyController));
            this.bot.getCommandRegistry().registerCommand("start", new MentionFilter((event, command) -> {
                try {
                    if (databaseController.getSpotifyUser(command.getSender().getId()).isPresent()) {
                        this.bot.perform(SendText.builder()
                                .chatId(command.getChat().getChatId())
                                .replyToMessageID(command.getBaseMessage().getMessageId())
                                .text("You are already authenticated with Spotify.")
                                .build());
                    } else {
                        URI uri = spotifyController.getAuthorizationUri(command.getSender().getId());
                        this.bot.perform(SendText.builder()
                                .chatId(command.getChat().getChatId())
                                .replyToMessageID(command.getBaseMessage().getMessageId())
                                .text("To authenticate with Spotify, please visit this link:\n" + uri.toString())
                                .build());
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    this.bot.perform(SendText.builder()
                            .chatId(command.getChat().getChatId())
                            .replyToMessageID(command.getBaseMessage().getMessageId())
                            .text("An error occurred. Please contact @nickrobson if it keeps happening.")
                            .build());
                }
                return true;
            }));
        });
    }

}
