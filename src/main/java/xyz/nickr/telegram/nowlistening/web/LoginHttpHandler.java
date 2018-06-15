package xyz.nickr.telegram.nowlistening.web;

import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import java.util.Optional;
import java.util.UUID;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;

/**
 * @author Nick Robson
 */
public class LoginHttpHandler extends HttpHandler {

    private final SpotifyController spotifyController;
    private final DatabaseController databaseController;

    public LoginHttpHandler(SpotifyController spotifyController, DatabaseController databaseController) {
        this.spotifyController = spotifyController;
        this.databaseController = databaseController;
    }

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
            if (!userId.isPresent()) {
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
            response.getWriter().write("Hey look, it worked!");
        } else {
            response.sendError(400, "An error occurred while communicating with Spotify.");
            System.err.println("Error while authenticating with Spotify: " + error);
        }
    }
    
}
