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
public class NowListeningMessage {

    private final long telegramUserId;
    private final String inlineMessageId;

}
