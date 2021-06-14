package xyz.nickr.telegram.nowlistening.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.InlineQuery;
import com.pengrad.telegrambot.model.request.InlineQueryResultArticle;
import com.pengrad.telegrambot.model.request.InputTextMessageContent;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerInlineQuery;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

import java.util.Optional;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class InlineQueryHandler {

    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;
    private final TelegramController telegramController;

    public void onInlineQuery(TelegramBot bot, InlineQuery inlineQuery) {
        try {
            long telegramUserId = inlineQuery.from().id();
            Optional<SpotifyUser> user = databaseController.getSpotifyUser(telegramUserId);
            if (user.isPresent()) {
                SpotifyPlayingData track = spotifyController.updatePlayingData(user.get()).orElse(null);
                bot.execute(
                        new AnswerInlineQuery(
                                inlineQuery.id(),
                                new InlineQueryResultArticle(
                                        TelegramController.NOW_LISTENING_MSG_UPDATE_FOREVER_ID,
                                        "Show what music you listen to.",
                                        new InputTextMessageContent(telegramController.getMessage(track).toHtml())
                                                .parseMode(ParseMode.HTML)
                                                .disableWebPagePreview(true)
                                )
                                        .description("I'll remain updated as you change songs until you delete the message.")
                                        .replyMarkup(telegramController.getKeyboard(track)),
                                new InlineQueryResultArticle(
                                        TelegramController.NOW_LISTENING_MSG_UPDATE_ONE_DAY_ID,
                                        "Show what music you listen to.",
                                        new InputTextMessageContent(telegramController.getMessage(track).toHtml())
                                                .parseMode(ParseMode.HTML)
                                                .disableWebPagePreview(true)
                                )
                                        .description("I'll remain updated as you change songs for a day.")
                                        .replyMarkup(telegramController.getKeyboard(track)),
                                new InlineQueryResultArticle(
                                        TelegramController.NOW_LISTENING_MSG_NO_UPDATE_ID,
                                        "Show what music you listen to.",
                                        new InputTextMessageContent(telegramController.getMessage(track).toHtml())
                                                .parseMode(ParseMode.HTML)
                                                .disableWebPagePreview(true)
                                )
                                        .description("This message will NOT auto-update.")
                                        .replyMarkup(telegramController.getKeyboard(track))
                        )
                                .cacheTime(0)
                                .isPersonal(true)
                );
            } else {
                telegramController.getBot().execute(
                        new AnswerInlineQuery(inlineQuery.id())
                                .switchPmText("Connect with Spotify")
                                .switchPmParameter(TelegramController.AUTH_WITH_SPOTIFY_ID)
                                .cacheTime(0)
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
