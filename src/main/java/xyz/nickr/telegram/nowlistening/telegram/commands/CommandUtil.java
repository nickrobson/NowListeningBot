package xyz.nickr.telegram.nowlistening.telegram.commands;

import com.jtelegram.api.util.TextBuilder;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;

public class CommandUtil {
    public static SendMessage send(Object chatId, TextBuilder textBuilder) {
        return new SendMessage(chatId, textBuilder.toHtml())
                .parseMode(ParseMode.HTML);
    }

    public static SendMessage reply(Object chatId, int messageId, TextBuilder textBuilder) {
        return send(chatId, textBuilder)
                .replyToMessageId(messageId);
    }

    public static SendMessage reply(Message message, TextBuilder textBuilder) {
        return reply(message.chat().id(), message.messageId(), textBuilder);
    }
}
