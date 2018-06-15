package xyz.nickr.telegram.nowplayingbot;

import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import java.net.URI;
import java.sql.SQLException;
import java.util.UUID;
import xyz.nickr.telegram.nowplayingbot.db.DatabaseController;

public class SpotifyController {

    private final SpotifyApi api;
    private final DatabaseController databaseController;

    public SpotifyController(JsonObject config, DatabaseController databaseController) {
        JsonObject spotify = config.getAsJsonObject("spotify");

        String clientId = spotify.getAsJsonPrimitive("client_id").getAsString();
        String clientSecret = spotify.getAsJsonPrimitive("client_secret").getAsString();
        URI redirectUri = URI.create(spotify.getAsJsonPrimitive("redirect_uri").getAsString());

        this.databaseController = databaseController;
        this.api = SpotifyApi.builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .build();
    }

    public SpotifyApi getApi() {
        return api;
    }

    public URI getAuthorizationUri(long telegramUserId) throws SQLException {
        UUID uuid = databaseController.getUUID(telegramUserId);

        return api.authorizationCodeUri()
                .state(uuid.toString())
                .scope("user-read-currently-playing,user-read-playback-state")
                .show_dialog(true)
                .build()
                .execute();
    }
}
