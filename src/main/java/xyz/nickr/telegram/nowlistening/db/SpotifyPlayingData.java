package xyz.nickr.telegram.nowlistening.db;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;

/**
 * @author Nick Robson
 */
@Data
@Wither
@Builder
@EqualsAndHashCode(exclude = "lastChecked")
public class SpotifyPlayingData {

    private final long telegramUserId;
    private final String lastTrackName;
    private final String lastTrackArtist;
    private final String lastTrackUrl;
    private final long lastChecked;
    private final boolean playing;

}
