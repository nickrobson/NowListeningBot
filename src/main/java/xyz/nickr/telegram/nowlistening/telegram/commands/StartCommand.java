package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.jtelegram.api.commands.Command;
import com.jtelegram.api.commands.CommandHandler;
import com.jtelegram.api.events.message.TextMessageEvent;
import com.jtelegram.api.requests.message.send.SendText;
import java.net.URI;
import java.sql.SQLException;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class StartCommand implements CommandHandler {

    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    @Override
    public void onCommand(TextMessageEvent event, Command command) {
        try {
            if (databaseController.getSpotifyUser(command.getSender().getId()).isPresent()) {
                event.getBot().perform(SendText.builder()
                        .chatId(command.getChat().getChatId())
                        .replyToMessageID(command.getBaseMessage().getMessageId())
                        .text("You are already authenticated with Spotify.\n" +
                                "You can use /gdpr to see and remove data on you, and access bot information.")
                        .errorHandler(Throwable::printStackTrace)
                        .build());
            } else {
                URI uri = spotifyController.getAuthorizationUri(command.getSender().getId());
                event.getBot().perform(SendText.builder()
                        .chatId(command.getChat().getChatId())
                        .replyToMessageID(command.getBaseMessage().getMessageId())
                        .text("To authenticate with Spotify, please visit this link:\n" + uri.toString())
                        .errorHandler(Throwable::printStackTrace)
                        .build());
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            event.getBot().perform(SendText.builder()
                    .chatId(command.getChat().getChatId())
                    .replyToMessageID(command.getBaseMessage().getMessageId())
                    .text("An error occurred. Please contact @nickrobson if it keeps happening.")
                    .errorHandler(Throwable::printStackTrace)
                    .build());
        }
    }
}
