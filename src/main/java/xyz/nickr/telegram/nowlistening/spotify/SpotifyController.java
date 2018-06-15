package xyz.nickr.telegram.nowlistening.spotify;

import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.SpotifyUser;

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

    public void updateSpotifyUser(long telegramUserId, AuthorizationCodeCredentials credentials) throws SQLException {
        SpotifyUser spotifyUser = databaseController.getSpotifyUser(telegramUserId)
                .orElseGet(() -> SpotifyUser.builder().build());

        spotifyUser = spotifyUser
                .withTelegramUserId(telegramUserId)
                .withAccessToken(credentials.getAccessToken())
                .withRefreshToken(credentials.getRefreshToken() != null ? credentials.getRefreshToken() : spotifyUser.getRefreshToken())
                .withTokenType(credentials.getTokenType())
                .withScope(credentials.getScope())
                .withExpiryDate(Instant.now().getEpochSecond() + credentials.getExpiresIn());

        databaseController.updateSpotifyUser(spotifyUser);
    }
}
