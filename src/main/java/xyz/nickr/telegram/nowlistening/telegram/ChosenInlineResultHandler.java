package xyz.nickr.telegram.nowlistening.telegram;

import com.pengrad.telegrambot.model.ChosenInlineResult;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class ChosenInlineResultHandler {

    private final DatabaseController databaseController;
    private final TelegramController telegramController;

    public void onChosenInlineResult(ChosenInlineResult chosenInlineResult) {
        try {
            String resultId = chosenInlineResult.resultId();

            boolean permanent = TelegramController.NOW_LISTENING_MSG_UPDATE_FOREVER_ID.equals(resultId);
            boolean isAutoUpdate = permanent || TelegramController.NOW_LISTENING_MSG_UPDATE_ONE_DAY_ID.equals(resultId);

            if (isAutoUpdate) {
                long telegramUserId = chosenInlineResult.from().id();
                String messageId = chosenInlineResult.inlineMessageId();
                databaseController.addNowListeningMessage(telegramUserId, messageId, permanent);
                telegramController.updateEnabledNowListeningMessages(telegramUserId);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
