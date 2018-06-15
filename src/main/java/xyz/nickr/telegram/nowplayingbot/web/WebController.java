package xyz.nickr.telegram.nowplayingbot.web;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import xyz.nickr.telegram.nowplayingbot.SpotifyController;
import xyz.nickr.telegram.nowplayingbot.db.DatabaseController;

/**
 * @author Nick Robson
 */
public class WebController {

    private final HttpServer server;

    public WebController(JsonObject config, SpotifyController spotifyController, DatabaseController databaseController) {
        JsonObject web = config.getAsJsonObject("web");
        int port = web.getAsJsonPrimitive("port").getAsInt();

        this.server = HttpServer.createSimpleServer(null, port);

        ServerConfiguration webConfig = server.getServerConfiguration();
        webConfig.addHttpHandler(new LoginHttpHandler(spotifyController, databaseController), "/login");
    }

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() {
        server.shutdown();
    }
}
