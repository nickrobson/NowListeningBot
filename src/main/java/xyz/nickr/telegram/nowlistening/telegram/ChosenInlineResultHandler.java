package xyz.nickr.telegram.nowlistening.telegram;

import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.inline.ChosenInlineResultEvent;
import com.jtelegram.api.inline.result.ChosenInlineResult;
import java.time.Instant;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.NowListeningMessage;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class ChosenInlineResultHandler implements EventHandler<ChosenInlineResultEvent> {

    private final DatabaseController databaseController;
    private final TelegramController telegramController;

    @Override
    public void onEvent(ChosenInlineResultEvent event) {
        try {
            ChosenInlineResult result = event.getChosenResult();
            long telegramUserId = result.getFrom().getId();
            String resultId = result.getResultId();
            String messageId = result.getInlineMessageId();

            if (TelegramController.NOW_LISTENING_MSG_UPDATE_ID.equals(resultId)) {
                databaseController.addNowListeningMessage(new NowListeningMessage(telegramUserId, messageId, Instant.now().getEpochSecond()));
                telegramController.updateNowListeningMessages(telegramUserId);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
