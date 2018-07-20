package xyz.nickr.telegram.nowlistening.db;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Wither;

/**
 * @author Nick Robson
 */
@Data
@Wither
@Builder
@ToString(exclude = {"accessToken", "refreshToken"})
public class SpotifyUser {

    private final long telegramUserId;
    private final String languageCode;

    private final String accessToken;
    private final String tokenType;
    private final String scope;
    private final long expiryDate;
    private final String refreshToken;

}
