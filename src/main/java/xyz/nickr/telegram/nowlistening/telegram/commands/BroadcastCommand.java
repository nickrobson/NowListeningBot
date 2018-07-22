package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.jtelegram.api.chat.id.ChatId;
import com.jtelegram.api.commands.Command;
import com.jtelegram.api.commands.CommandHandler;
import com.jtelegram.api.events.message.TextMessageEvent;
import com.jtelegram.api.inline.keyboard.InlineKeyboardMarkup;
import com.jtelegram.api.menu.MenuHandler;
import com.jtelegram.api.menu.MenuRow;
import com.jtelegram.api.menu.SimpleMenu;
import com.jtelegram.api.menu.SimpleMenuButton;
import com.jtelegram.api.menu.viewer.RegularMenuViewer;
import com.jtelegram.api.requests.message.edit.EditTextMessage;
import com.jtelegram.api.requests.message.send.SendText;
import com.jtelegram.api.util.TextBuilder;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class BroadcastCommand implements CommandHandler {

    private final DatabaseController databaseController;

    @Override
    public void onCommand(TextMessageEvent event, Command command) {
        if (command.getSender().getId() != 112972102L)
            return;
        final Set<Long> users;
        try {
            users = this.databaseController.getAllUserIds();
        } catch (SQLException ex) {
            ex.printStackTrace();
            event.getBot().perform(SendText.builder()
                    .chatId(command.getChat().getChatId())
                    .replyToMessageID(command.getBaseMessage().getMessageId())
                    .text(TextBuilder.create().escaped("An error occurred while fetching users."))
                    .build());
            return;
        }
        final String broadcastText = command.getArgsAsText().trim();
        if (broadcastText.isEmpty()) {
            event.getBot().perform(SendText.builder()
                    .chatId(command.getChat().getChatId())
                    .replyToMessageID(command.getBaseMessage().getMessageId())
                    .text(TextBuilder.create().escaped(""))
                    .build());
            return;
        }

        event.getBot().perform(SendText.builder()
                .chatId(command.getChat().getChatId())
                .replyToMessageID(command.getBaseMessage().getMessageId())
                .text(TextBuilder.create()
                        .escaped("Click 'Confirm' to broadcast to ")
                        .escaped(String.valueOf(users.size()))
                        .escaped(" users:")
                        .newLine().newLine().escaped(broadcastText))
                .callback(message -> {
                    final SimpleMenu menu = SimpleMenu.builder().bot(event.getBot()).build();
                    menu.addRow(MenuRow.from(SimpleMenuButton.builder()
                            .onPress((button, clickEvent) -> {
                                MenuHandler.unregisterMenu(menu);

                                AtomicInteger success = new AtomicInteger(0);
                                CountDownLatch latch = new CountDownLatch(users.size());

                                users.forEach(userId -> {
                                    event.getBot().perform(SendText.builder()
                                            .chatId(ChatId.of(userId))
                                            .text(TextBuilder.create().escaped(broadcastText))
                                            .callback(m -> {
                                                latch.countDown();
                                                success.incrementAndGet();
                                            })
                                            .errorHandler(ex -> {
                                                ex.printStackTrace();
                                                latch.countDown();
                                            })
                                            .build());
                                });

                                try {
                                    latch.await(users.size() * 2, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                event.getBot().perform(EditTextMessage.builder()
                                        .chatId(message.getChat().getChatId())
                                        .messageId(message.getMessageId())
                                        .replyMarkup(InlineKeyboardMarkup.builder().build())
                                        .text(TextBuilder.create()
                                                .escaped("Broadcast sent successfully to ")
                                                .escaped(Integer.toString(success.get()))
                                                .escaped("/")
                                                .escaped(Integer.toString(users.size()))
                                                .escaped(" users.")
                                                .newLine().newLine().escaped(broadcastText))
                                        .build());
                                return false;
                            })
                            .label("Confirm")
                            .build()));
                    menu.addViewer(RegularMenuViewer.builder()
                            .chatId(message.getChat().getChatId())
                            .messageId(message.getMessageId())
                            .build());
                    MenuHandler.registerMenu(menu);
                })
                .build());
    }

}
