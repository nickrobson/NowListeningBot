package xyz.nickr.telegram.nowlistening.scheduler;

import java.util.Set;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class PlayingTrackRefresher implements Runnable {

    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    @Override
    public void run() {
        try {
            Set<SpotifyUser> userSet = databaseController.getUsersWithValidAccess();
            for (SpotifyUser user : userSet) {
                try {
                    spotifyController.updatePlayingData(user);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
