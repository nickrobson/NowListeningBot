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
public class NowListeningMessage {

    private final long telegramUserId;
    private final String inlineMessageId;

}
