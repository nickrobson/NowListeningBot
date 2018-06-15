package xyz.nickr.telegram.nowlistening.scheduler;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.SpotifyUser;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class PlayingTrackRefresher implements Runnable {

    private final DatabaseController databaseController;

    @Override
    public void run() {
        try {
            Set<SpotifyUser> userSet = databaseController.getUsersWithValidAccess();
            for (SpotifyUser user : userSet) {
                try {
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
                    } else {
                        Optional<SpotifyPlayingData> playingData = databaseController.getPlayingData(user.getTelegramUserId());
                        if (playingData.isPresent()) {
                            databaseController.updatePlayingData(playingData.get().withPlaying(false));
                        }
                    }
                } catch (IOException | SpotifyWebApiException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
