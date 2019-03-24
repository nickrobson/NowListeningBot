package xyz.nickr.telegram.nowlistening.telegram;

import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.inline.InlineQuery;
import com.jtelegram.api.inline.input.InputTextMessageContent;
import com.jtelegram.api.inline.result.InlineResultArticle;
import com.jtelegram.api.requests.inline.AnswerInlineQuery;
import java.util.Optional;
import lombok.AllArgsConstructor;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyPlayingData;
import xyz.nickr.telegram.nowlistening.db.models.SpotifyUser;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;

/**
 * @author Nick Robson
 */
@AllArgsConstructor
public class InlineQueryHandler implements EventHandler<InlineQueryEvent> {

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
                telegramController.getBot().perform(AnswerInlineQuery.builder()
                        .queryId(query.getId())
                        .addResult(InlineResultArticle.builder()
                                .id(TelegramController.NOW_LISTENING_MSG_UPDATE_FOREVER_ID)
                                .title("Show what music you listen to.")
                                .description("I'll remain updated as you change songs until you delete the message.")
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(telegramController.getMessage(track))
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(telegramController.getKeyboard(track))
                                .build())
                        .addResult(InlineResultArticle.builder()
                                .id(TelegramController.NOW_LISTENING_MSG_UPDATE_ONE_DAY_ID)
                                .title("Show what music you listen to.")
                                .description("I'll remain updated as you change songs for a day.")
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(telegramController.getMessage(track))
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(telegramController.getKeyboard(track))
                                .build())
                        .addResult(InlineResultArticle.builder()
                                .id(TelegramController.NOW_LISTENING_MSG_NO_UPDATE_ID)
                                .title("Show what music you listen to.")
                                .description("This message will NOT auto-update.")
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText(telegramController.getMessage(track))
                                        .disableWebPagePreview(true)
                                        .build())
                                .replyMarkup(telegramController.getKeyboard(track))
                                .build())
                        .cacheTime(0)
                        .isPersonal(true)
                        .errorHandler(Throwable::printStackTrace)
                        .build());
            } else {
                telegramController.getBot().perform(AnswerInlineQuery.builder()
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
