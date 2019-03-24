package xyz.nickr.telegram.nowlistening.telegram;

import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.inline.keyboard.CallbackQueryEvent;
import java.sql.SQLException;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.NowListeningMessage;

import static xyz.nickr.telegram.nowlistening.telegram.TelegramController.CONTINUE_GETTING_UPDATES;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class CallbackQueryHandler implements EventHandler<CallbackQueryEvent> {

    private final DatabaseController databaseController;
    private final TelegramController telegramController;

    @Override
    public void onEvent(CallbackQueryEvent event) {
        try {
            if (CONTINUE_GETTING_UPDATES.equals(event.getQuery().getData())) {
                long telegramUserId = event.getQuery().getFrom().getId();
                String inlineMessageId = event.getQuery().getInlineMessageId();
                databaseController.getNowListeningMessage(telegramUserId, inlineMessageId)
                    .ifPresent(message -> {
                        try {
                            databaseController.enableMessage(message);
                            telegramController.updateMessage(message);
                        } catch (SQLException ex) {
                            ex.printStackTrace();
                        }
                    });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
