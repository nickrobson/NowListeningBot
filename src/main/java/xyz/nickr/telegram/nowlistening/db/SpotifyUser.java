package xyz.nickr.telegram.nowlistening.db;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Wither;

/**
 * @author Nick Robson
 */
@Data
@Wither
@Builder
public class SpotifyUser {

    private final long telegramUserId;
    private final String languageCode;

    private final String accessToken;
    private final String tokenType;
    private final String scope;
    private final long expiryDate;
    private final String refreshToken;

}
