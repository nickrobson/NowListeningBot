package xyz.nickr.telegram.nowlistening.scheduler;

import java.sql.SQLException;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.telegram.TelegramController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class MessageDisabler implements Runnable {

    private TelegramController telegramController;

    @Override
    public void run() {
        try {
            int disabled = telegramController.updateDisabledNowListeningMessages();
            if (disabled > 0) {
                System.out.format("[NowListening] Disabled %d expired messages.\n", disabled);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
