package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

import java.net.URI;
import java.sql.SQLException;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class StartCommand {

    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    public void onCommand(TelegramBot bot, Message message) {
        long telegramUserId = message.from().id();
        Long telegramChatId = message.chat().id();
        try {
            if (databaseController.getSpotifyUser(telegramUserId).isPresent()) {
                try {
                    String replyText = "You are already authenticated with Spotify.\n" +
                            "You can use /gdpr to see and remove data on you, and access bot information.";
                    bot.execute(
                            new SendMessage(telegramChatId, replyText)
                                    .replyToMessageId(message.messageId())
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                URI uri = spotifyController.getAuthorizationUri(telegramUserId);
                try {
                    String replyText = "To authenticate with Spotify, please visit this link:\n" + uri.toString();
                    bot.execute(
                            new SendMessage(telegramChatId, replyText)
                                    .replyToMessageId(message.messageId())
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            try {
                String replyText = "An error occurred. Please contact @nickrobson if it keeps happening.";
                bot.execute(
                        new SendMessage(telegramChatId, replyText)
                                .replyToMessageId(message.messageId())
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
