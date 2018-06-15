package xyz.nickr.telegram.nowplayingbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import xyz.nickr.telegram.nowplayingbot.db.DatabaseController;
import xyz.nickr.telegram.nowplayingbot.web.WebController;

/**
 * @author Nick Robson
 */
public class NowPlayingBot {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws IOException {
        JsonObject config;
        Path configJson = Paths.get("config.json");
        try (BufferedReader reader = Files.newBufferedReader(configJson, StandardCharsets.UTF_8)) {
            config = NowPlayingBot.GSON.fromJson(reader, JsonObject.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(configJson.toString() + " does not contain valid JSON", ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read config from " + configJson.toString(), ex);
        }

        DatabaseController databaseController = new DatabaseController(config);
        SpotifyController spotifyController = new SpotifyController(config, databaseController);
        WebController webController = new WebController(config, spotifyController, databaseController);

        try {
            System.out.println(spotifyController.getAuthorizationUri(112972102L));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        webController.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //noinspection Convert2MethodRef
            webController.shutdown();
        }, "NowPlayingBot Shutdown Thread"));

        while (true) {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

}
