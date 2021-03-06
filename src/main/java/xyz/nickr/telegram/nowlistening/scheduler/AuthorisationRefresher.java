package xyz.nickr.telegram.nowlistening.scheduler;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.exceptions.detailed.BadRequestException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class AuthorisationRefresher implements Runnable {

    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;

    @Override
    public void run() {
        try {
            Set<SpotifyUser> userSet = databaseController.getUsersRequiringReauthorisation();
            for (SpotifyUser user : userSet) {
                try {
                    AuthorizationCodeCredentials credentials = SpotifyApi.builder()
                            .setClientId(spotifyController.getApi().getClientId())
                            .setClientSecret(spotifyController.getApi().getClientSecret())
                            .setRefreshToken(user.getRefreshToken()).build()
                            .authorizationCodeRefresh().build().execute();

                    spotifyController.updateSpotifyUser(user.getTelegramUserId(), credentials);
                    System.out.format("[NowListening] Refreshed tokens for %d.\n", user.getTelegramUserId());
                } catch (BadRequestException ex) {
                    if ("Refresh token revoked".equals(ex.getMessage())) {
                        try {
                            databaseController.deleteSpotifyUser(user.getTelegramUserId());
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        try {
                            databaseController.deletePlayingData(user.getTelegramUserId());
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        try {
                            databaseController.deleteAllMessages(user.getTelegramUserId());
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (SQLException | SpotifyWebApiException | IOException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
