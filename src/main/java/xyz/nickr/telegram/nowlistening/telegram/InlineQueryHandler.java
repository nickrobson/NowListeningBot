package xyz.nickr.telegram.nowlistening.telegram;

import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.inline.InlineQuery;
import com.jtelegram.api.inline.input.InputTextMessageContent;
import com.jtelegram.api.inline.keyboard.InlineKeyboardButton;
import com.jtelegram.api.inline.keyboard.InlineKeyboardMarkup;
import com.jtelegram.api.inline.keyboard.InlineKeyboardRow;
import com.jtelegram.api.inline.result.InlineResultArticle;
import com.jtelegram.api.requests.inline.AnswerInlineQuery;
import com.jtelegram.api.requests.message.framework.ParseMode;
import com.jtelegram.api.util.TextBuilder;
import java.util.Optional;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.SpotifyUser;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class InlineQueryHandler implements EventHandler<InlineQueryEvent> {

    private final TelegramBot bot;
    private final DatabaseController databaseController;
    private final SpotifyController spotifyController;
    private final TelegramController telegramController;

    @Override
    public void onEvent(InlineQueryEvent event) {
        try {
            InlineQuery query = event.getQuery();
            long telegramUserId = query.getFrom().getId();
            Optional<SpotifyUser> user = databaseController.getSpotifyUser(telegramUserId);
            if (user.isPresent()) {
                SpotifyPlayingData track = spotifyController.updatePlayingData(user.get()).orElse(null);
                if (track == null || !track.isPlaying()) {
                    bot.perform(AnswerInlineQuery.builder()
                            .queryId(query.getId())
                            .addResult(InlineResultArticle.builder()
                                    .id(TelegramController.NOW_LISTENING_MSG_ID)
                                    .title("Show what music you listen to.")
                                    .description("I'll auto-update as you change songs.")
                                    .inputMessageContent(InputTextMessageContent.builder()
                                            .messageText(telegramController.getMessage(track))
                                            .disableWebPagePreview(true)
                                            .build())
                                    .replyMarkup(telegramController.getKeyboard(track))
                                    .build())
                            .cacheTime(0)
                            .errorHandler(Throwable::printStackTrace)
                            .build());
                } else {
                    bot.perform(AnswerInlineQuery.builder()
                            .queryId(query.getId())
                            .addResult(InlineResultArticle.builder()
                                    .id(TelegramController.NOW_LISTENING_MSG_ID)
                                    .title("Show what music you listen to.")
                                    .description("I'll auto-update as you change songs.")
                                    .inputMessageContent(InputTextMessageContent.builder()
                                            .messageText(telegramController.getMessage(track))
                                            .disableWebPagePreview(true)
                                            .build())
                                    .replyMarkup(telegramController.getKeyboard(track))
                                    .build())
                            .cacheTime(0)
                            .errorHandler(Throwable::printStackTrace)
                            .build());
                }
            } else {
                bot.perform(AnswerInlineQuery.builder()
                        .queryId(query.getId())
                        .switchPmText("Connect with Spotify")
                        .switchPmParameter(TelegramController.AUTH_WITH_SPOTIFY_ID)
                        .cacheTime(0)
                        .errorHandler(Throwable::printStackTrace)
                        .build());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
