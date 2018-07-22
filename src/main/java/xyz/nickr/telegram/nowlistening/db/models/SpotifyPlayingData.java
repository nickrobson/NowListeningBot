package xyz.nickr.telegram.nowlistening.db.models;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = "lastChecked")
public class SpotifyPlayingData extends NLModel {

    @Expose private final long telegramUserId;
    @Expose private final String lastTrackName;
    @Expose private final String lastTrackArtist;
    @Expose private final String lastTrackUrl;
    @Expose private final long lastChecked;
    @Expose private final boolean playing;

}
