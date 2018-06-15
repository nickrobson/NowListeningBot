package xyz.nickr.telegram.nowlistening.spotify;

import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.SpotifyPlayingData;
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

    public Optional<SpotifyPlayingData> updatePlayingData(SpotifyUser user) throws SQLException, IOException, SpotifyWebApiException {
        CurrentlyPlaying currentlyPlaying = SpotifyApi.builder()
                .setAccessToken(user.getAccessToken()).build()
                .getUsersCurrentlyPlayingTrack().build().execute();

        if (currentlyPlaying.getIs_playing()) {
            Track track = currentlyPlaying.getItem();

            SpotifyPlayingData playingData = SpotifyPlayingData.builder()
                    .telegramUserId(user.getTelegramUserId())
                    .lastTrackName(track.getName())
                    .lastTrackArtist(
                            Arrays.stream(track.getArtists())
                                    .map(ArtistSimplified::getName)
                                    .collect(Collectors.joining(", ")))
                    .lastTrackUrl(track.getHref())
                    .lastChecked(Instant.now().getEpochSecond())
                    .playing(true)
                    .build();

            databaseController.updatePlayingData(playingData);

            return Optional.of(playingData);
        } else {
            Optional<SpotifyPlayingData> playingData = databaseController.getPlayingData(user.getTelegramUserId());
            if (playingData.isPresent()) {
                SpotifyPlayingData data = playingData.get().withPlaying(false);
                databaseController.updatePlayingData(data);
                return Optional.of(data);
            }
            return Optional.empty();
        }
    }
}
