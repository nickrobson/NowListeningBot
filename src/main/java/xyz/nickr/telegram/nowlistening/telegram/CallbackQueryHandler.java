package xyz.nickr.telegram.nowlistening.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.telegram.commands.GdprCommand;

import java.sql.SQLException;

import static xyz.nickr.telegram.nowlistening.telegram.TelegramController.CONTINUE_GETTING_UPDATES;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class CallbackQueryHandler {

    private final DatabaseController databaseController;
    private final TelegramController telegramController;
    private final GdprCommand gdprCommand;

    public void onCallbackQuery(CallbackQuery callbackQuery) {
        try {
            long telegramUserId = callbackQuery.from().id();
            if (CONTINUE_GETTING_UPDATES.equals(callbackQuery.data())) {
                String inlineMessageId = callbackQuery.inlineMessageId();
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
            if (callbackQuery.data() != null && callbackQuery.data().startsWith("gdpr/")) {
                gdprCommand.handleButtonClick(telegramUserId, callbackQuery.message(), callbackQuery.data());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
