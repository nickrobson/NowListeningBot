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
@EqualsAndHashCode(
        callSuper = false,
        of = {"telegramUserId", "inlineMessageId"})
public class NowListeningMessage extends NLModel {

    private final long id;
    @Expose private final long telegramUserId;
    @Expose private final String inlineMessageId;
    @Expose private final long timeAdded;
    @Expose private final boolean enabled;

}
