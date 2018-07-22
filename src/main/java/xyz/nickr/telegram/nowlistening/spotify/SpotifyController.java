package xyz.nickr.telegram.nowlistening.spotify;

import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;

public class SpotifyController {

    private final SpotifyApi api;
    private final DatabaseController databaseController;
    private final List<PlayingDataConsumer> listeners = new ArrayList<>();

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

    public void addListener(PlayingDataConsumer consumer) {
        listeners.add(Objects.requireNonNull(consumer, "listener can't be null"));
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
        try {
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
        } catch (Exception ex) {
            throw new RuntimeException("Failed to update Spotify user for " + telegramUserId, ex);
        }
    }

    public Optional<SpotifyPlayingData> updatePlayingData(SpotifyUser user) {
        try {
            Optional<SpotifyPlayingData> oldPlayingData = databaseController.getPlayingData(user.getTelegramUserId());

            CurrentlyPlaying currentlyPlaying = SpotifyApi.builder()
                    .setAccessToken(user.getAccessToken()).build()
                    .getUsersCurrentlyPlayingTrack().build().execute();

            if (currentlyPlaying != null && currentlyPlaying.getItem() != null) {
                Track track = currentlyPlaying.getItem();

                SpotifyPlayingData playingData = SpotifyPlayingData.builder()
                        .telegramUserId(user.getTelegramUserId())
                        .lastTrackName(track.getName())
                        .lastTrackArtist(
                                Arrays.stream(track.getArtists())
                                        .map(ArtistSimplified::getName)
                                        .collect(Collectors.joining(", ")))
                        .lastTrackUrl(track.getExternalUrls().get("spotify"))
                        .lastChecked(Instant.now().getEpochSecond())
                        .playing(currentlyPlaying.getIs_playing())
                        .build();

                databaseController.updatePlayingData(playingData);
                playingDataChanged(oldPlayingData, playingData);
                return Optional.of(playingData);
            } else {
                if (oldPlayingData.isPresent()) {
                    SpotifyPlayingData data = oldPlayingData.get().withPlaying(false);
                    databaseController.updatePlayingData(data);
                    playingDataChanged(oldPlayingData, data);
                    return Optional.of(data);
                }
                return Optional.empty();
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Spotify playing data for " + user, ex);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void playingDataChanged(Optional<SpotifyPlayingData> oldPlayingData, SpotifyPlayingData newPlayingData) {
        if (!oldPlayingData.isPresent() || !oldPlayingData.get().equals(newPlayingData)) {
            listeners.forEach(listener -> {
                try {
                    listener.accept(newPlayingData);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }

    public interface PlayingDataConsumer {
        void accept(SpotifyPlayingData playingData) throws Exception;
    }
}
