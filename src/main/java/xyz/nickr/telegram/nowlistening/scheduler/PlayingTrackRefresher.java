package xyz.nickr.telegram.nowlistening.scheduler;

import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.SpotifyUser;
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
                } catch (SQLException | IOException | SpotifyWebApiException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
