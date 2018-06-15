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

    @Override
    public void onEvent(InlineQueryEvent event) {
        try {
            InlineQuery query = event.getQuery();
            long telegramUserId = query.getFrom().getId();
            Optional<SpotifyUser> user = databaseController.getSpotifyUser(telegramUserId);
            if (user.isPresent()) {
                Optional<SpotifyPlayingData> playingData = spotifyController.updatePlayingData(user.get());
                if (!playingData.isPresent() || !playingData.get().isPlaying()) {
                    bot.perform(AnswerInlineQuery.builder()
                            .queryId(query.getId())
                            .addResult(InlineResultArticle.builder()
                                    .id("SendMusicMessage")
                                    .title("Show what music you listen to.")
                                    .description("The message will auto-update as you change songs.")
                                    .inputMessageContent(InputTextMessageContent.builder()
                                            .messageText("I'm not listening to Spotify right now \uD83D\uDD07")
                                            .disableWebPagePreview(true)
                                            .build())
                                    .build())
                            .cacheTime(0)
                            .build());
                } else {
                    SpotifyPlayingData track = playingData.get();
                    TextBuilder messageText = TextBuilder.create()
                            .escaped("\uD83C\uDFB5 I'm listening to ")
                            .bold(track.getLastTrackName())
                            .escaped(" by ")
                            .italics(track.getLastTrackArtist())
                            .escaped(" \uD83C\uDFB5");

                    bot.perform(AnswerInlineQuery.builder()
                            .queryId(query.getId())
                            .addResult(InlineResultArticle.builder()
                                    .id("SendMusicMessage")
                                    .title("Show what music you listen to.")
                                    .description("The message will auto-update as you change songs.")
                                    .inputMessageContent(InputTextMessageContent.builder()
                                            .messageText(messageText)
                                            .disableWebPagePreview(true)
                                            .build())
                                    .replyMarkup(InlineKeyboardMarkup.builder()
                                            .keyboard(InlineKeyboardRow.builder()
                                                    .button(InlineKeyboardButton.builder()
                                                            .label("Open in Spotify")
                                                            .url(track.getLastTrackUrl())
                                                            .build())
                                                    .build())
                                            .build())
                                    .build())
                            .cacheTime(0)
                            .build());
                }
            } else {
                bot.perform(AnswerInlineQuery.builder()
                        .queryId(query.getId())
                        .switchPmText("Connect with Spotify")
                        .switchPmParameter("AuthSpotify")
                        .cacheTime(0)
                        .build());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
