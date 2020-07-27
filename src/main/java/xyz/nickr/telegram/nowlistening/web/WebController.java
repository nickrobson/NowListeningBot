package xyz.nickr.telegram.nowlistening.web;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import xyz.nickr.telegram.nowlistening.spotify.SpotifyController;
import xyz.nickr.telegram.nowlistening.db.DatabaseController;
import xyz.nickr.telegram.nowlistening.telegram.TelegramController;

/**
 * @author Nick Robson
 */
public class WebController {

    private final HttpServer server;

    public WebController(JsonObject config, DatabaseController databaseController, SpotifyController spotifyController, TelegramController telegramController) {
        JsonObject web = config.getAsJsonObject("webserver");
        int port = web.getAsJsonPrimitive("port").getAsInt();

        this.server = HttpServer.createSimpleServer(null, "127.0.0.1", port);

        ServerConfiguration webConfig = server.getServerConfiguration();
        webConfig.addHttpHandler(new LoginHttpHandler(spotifyController, databaseController, telegramController), "/login");
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() {
        server.shutdown();
    }
}
