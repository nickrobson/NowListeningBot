package xyz.nickr.telegram.nowlistening.db.models;

import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Wither;

/**
 * @author Nick Robson
 */
@Data
@Wither
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(exclude = {"accessToken", "refreshToken"})
public class SpotifyUser extends NLModel {

    @Expose private final long telegramUserId;
    @Expose private final String languageCode;

    private final String accessToken;
    @Expose private final String tokenType;
    @Expose private final String scope;
    @Expose private final long expiryDate;
    private final String refreshToken;

}
