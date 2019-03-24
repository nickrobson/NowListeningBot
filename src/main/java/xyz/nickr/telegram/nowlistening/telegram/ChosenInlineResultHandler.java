package xyz.nickr.telegram.nowlistening.telegram;

import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.inline.ChosenInlineResultEvent;
import com.jtelegram.api.inline.result.ChosenInlineResult;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;

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
            String resultId = result.getResultId();

            boolean permanent = TelegramController.NOW_LISTENING_MSG_UPDATE_FOREVER_ID.equals(resultId);
            boolean isAutoUpdate = permanent || TelegramController.NOW_LISTENING_MSG_UPDATE_ONE_DAY_ID.equals(resultId);

            if (isAutoUpdate) {
                long telegramUserId = result.getFrom().getId();
                String messageId = result.getInlineMessageId();
                databaseController.addNowListeningMessage(telegramUserId, messageId, permanent);
                telegramController.updateEnabledNowListeningMessages(telegramUserId);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
