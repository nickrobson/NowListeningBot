package xyz.nickr.telegram.nowlistening;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.scheduler.AuthorisationRefresher;
import xyz.nickr.telegram.nowlistening.scheduler.PlayingTrackRefresher;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.telegram.TelegramController;
import xyz.nickr.telegram.nowlistening.web.WebController;

/*
 * TODO
 *
 * [X] Authorisation with Spotify
 * [X] Scheduler refreshing authorisation when necessary
 * [X] Scheduler checking each user's current song
 * [X] Allow sending messages that show your current song
 * [ ] Updating sent messages to keep the current song up-to-date :')
 * [ ] Sending out the bot's @-mention so others can use it
 */
public class NowListening {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(4);

    public static void main(String[] args) throws IOException {
        Path configJson = Paths.get("config.json");

        if (!configJson.toFile().exists()) {
            try (BufferedWriter writer = Files.newBufferedWriter(configJson, StandardOpenOption.CREATE_NEW)) {
                InputStream inputStream = NowListening.class.getResourceAsStream("/config.json");
                try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                    int c;
                    char[] buf = new char[4096];
                    while ((c = reader.read(buf)) > 0) {
                        writer.write(buf, 0, c);
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to copy default configuration", ex);
            }
        }

        JsonObject config;
        try (BufferedReader reader = Files.newBufferedReader(configJson, StandardCharsets.UTF_8)) {
            config = NowListening.GSON.fromJson(reader, JsonObject.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(configJson.toString() + " does not contain valid JSON", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read config from " + configJson.toString(), ex);
        }

        DatabaseController databaseController = new DatabaseController(config);
        SpotifyController spotifyController = new SpotifyController(config, databaseController);
        WebController webController = new WebController(config, databaseController, spotifyController);
        TelegramController telegramController = new TelegramController(config, databaseController, spotifyController);

        EXECUTOR.scheduleWithFixedDelay(new AuthorisationRefresher(databaseController, spotifyController), 0L, 30L, TimeUnit.SECONDS);
        EXECUTOR.scheduleWithFixedDelay(new PlayingTrackRefresher(databaseController, spotifyController), 10L, 15L, TimeUnit.SECONDS);

        webController.start();
        telegramController.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //noinspection Convert2MethodRef
            webController.shutdown();
        }, "NowListening Shutdown Thread"));

        while (true) {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

}
