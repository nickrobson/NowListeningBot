package xyz.nickr.telegram.nowlistening.web;

import com.jtelegram.api.util.TextBuilder;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import lombok.AllArgsConstructor;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.http.util.HttpStatus;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.telegram.TelegramController;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class LoginHttpHandler extends HttpHandler {

    private final SpotifyController spotifyController;
    private final DatabaseController databaseController;
    private final TelegramController telegramController;

    @Override
    public void service(Request request, Response response) throws Exception {
        String state = request.getParameter("state");
        String code = request.getParameter("code");
        String error = request.getParameter("error");

        if (state == null || (code == null && error == null)) {
            response.sendError(400, "Invalid request");
            return;
        }

        long telegramUserId;
        UUID uuid;
        try {
            uuid = UUID.fromString(state);
            Optional<Long> userId = databaseController.getTelegramUserId(uuid);
            if (userId.isEmpty()) {
                response.sendError(400, "Invalid state");
                return;
            }
            telegramUserId = userId.get();
        } catch (Exception ex) {
            response.sendError(400, "Invalid state");
            return;
        }

        if (code != null) {
            AuthorizationCodeCredentials credentials = spotifyController.getApi()
                    .authorizationCode(code)
                    .build()
                    .execute();

            spotifyController.updateSpotifyUser(telegramUserId, credentials);

            response.setStatus(HttpStatus.OK_200);
            response.setContentType(ContentType.newContentType("text/html"));
            response.getWriter().write("<html><body><a href=\"tg://" + telegramController.getBotUsername() + "\">Back to Telegram</a></body></html>");

            telegramController.getBot().execute(
                    new SendMessage(
                            telegramUserId,
                            TextBuilder.create()
                                    .bold("All set!")
                                    .newLine().newLine()
                                    .escaped("Now that Spotify's connected, you can show off your music by typing ")
                                    .code("@" + telegramController.getBotUsername())
                                    .escaped(" in your chat box!")
                                    .newLine().newLine()
                                    .escaped("You can choose whether to keep the message updated with what you're listening to or just only show what you're listening at that moment.")
                                    .newLine().newLine()
                                    .escaped("You can use /gdpr to access and remove data that has been stored on you.")
                                    .toHtml()
                    )
                            .parseMode(ParseMode.HTML)
                            .disableWebPagePreview(true));
        } else {
            response.sendError(400, "An error occurred while communicating with Spotify.");
            System.err.println("Error while authenticating with Spotify: " + error);
        }
    }
    
}
