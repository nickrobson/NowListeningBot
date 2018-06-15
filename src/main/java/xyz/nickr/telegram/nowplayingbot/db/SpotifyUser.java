package xyz.nickr.telegram.nowplayingbot.db;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Wither;

/**
 * @author Nick Robson
 */
@Getter
@Wither
@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class SpotifyUser {

    private final long telegramUserId;
    private final String languageCode;

    private final String accessToken;
    private final String tokenType;
    private final String scope;
    private final long expiryDate;
    private final String refreshToken;

}
