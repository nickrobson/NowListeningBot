package xyz.nickr.telegram.nowplayingbot.db;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Nick Robson
 */
public class DatabaseController {

    private final String url;
    private Connection connection;

    public DatabaseController(JsonObject config) {
        JsonObject db = config.getAsJsonObject("db");

        this.url = db.getAsJsonPrimitive("url").getAsString();

        Objects.requireNonNull(this.url, "Database connection URL may not be null");

        try {
            createTables();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to create SQL tables", ex);
        }
    }

    public <T> T withConnection(ConnectionConsumer<T> consumer) throws SQLException {
        return withConnection(consumer, 0);
    }

    private <T> T withConnection(ConnectionConsumer<T> consumer, int retries) throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection(this.url);
        }
        try {
            return consumer.consume(connection);
        } catch (SQLRecoverableException ex) {
            this.connection.close();
            this.connection = null;
            if (retries < 5) {
                return withConnection(consumer, retries + 1);
            } else {
                throw ex;
            }
        }
    }

    public void createTables() throws SQLException {
        withConnection(connection -> {
            Statement statement = connection.createStatement();
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS uuids (" +
                            "id INTEGER PRIMARY KEY," +
                            "telegram_user INTEGER UNIQUE NOT NULL," +
                            "uuid STRING UNIQUE NOT NULL" +
                            ")");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS spotify_user (" +
                            "id INTEGER PRIMARY KEY," +
                            "telegram_user INTEGER UNIQUE NOT NULL," +
                            "language_code STRING," +
                            "access_token STRING," +
                            "token_type STRING," +
                            "scope STRING," +
                            "expiry_date INTEGER," +
                            "refresh_token STRING" +
                            ")");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS spotify_playing_data (" +
                            "id INTEGER PRIMARY KEY," +
                            "telegram_user INTEGER UNIQUE NOT NULL," +
                            "last_track_name STRING," +
                            "last_track_artist STRING," +
                            "last_track_url STRING," +
                            "last_checked INTEGER" +
                            ")");
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS now_listening_messages (" +
                            "id INTEGER PRIMARY KEY," +
                            "telegram_user INTEGER UNIQUE NOT NULL," +
                            "inline_message_id STRING UNIQUE NOT NULL" +
                            ")");
            return null;
        });
    }

    public Optional<Long> getTelegramUserId(UUID uuid) throws SQLException {
        return withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM uuids WHERE uuid = ?"
            );
            preparedStatement.setString(1, uuid.toString());

            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getLong("telegram_user"));
            }
            return Optional.empty();
        });
    }

    public UUID getUUID(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM uuids WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);
                ResultSet rs = preparedStatement.executeQuery();
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }

            UUID uuid;
            do {
                uuid = UUID.randomUUID();
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "INSERT INTO uuids (telegram_user, uuid) VALUES (?, ?)"
                )) {
                    preparedStatement.setLong(1, telegramUserId);
                    preparedStatement.setString(2, uuid.toString());
                    try {
                        preparedStatement.execute();
                        break;
                    } catch (SQLIntegrityConstraintViolationException ignored) {
                    }
                }
            } while (true);
            return uuid;
        });
    }

    public void deleteUUID(UUID uuid) throws SQLException {
        withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM uuids WHERE uuid = ?"
            );
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.execute();
            return null;
        });
    }

    public Optional<SpotifyUser> getSpotifyUser(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_user WHERE telegram_user = ?"
            );
            preparedStatement.setLong(1, telegramUserId);

            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                long storedTelegramUserId = rs.getLong("telegram_user");
                String languageCode = rs.getString("language_code");
                String accessToken = rs.getString("access_token");
                String tokenType = rs.getString("token_type");
                String scope = rs.getString("scope");
                long expiryDate = rs.getLong("expiry_date");
                String refreshToken = rs.getString("refresh_token");
                return Optional.of(new SpotifyUser(storedTelegramUserId, languageCode, accessToken, tokenType, scope, expiryDate, refreshToken));
            }
            return Optional.empty();
        });
    }

    public void updateSpotifyUser(SpotifyUser user) throws SQLException {
        withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO spotify_user " +
                            "(telegram_user, language_code, access_token, token_type, " +
                            "scope, expiry_date, refresh_token)" +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            preparedStatement.setLong(1, user.getTelegramUserId());
            preparedStatement.setString(2, user.getLanguageCode());
            preparedStatement.setString(3, user.getAccessToken());
            preparedStatement.setString(4, user.getTokenType());
            preparedStatement.setString(5, user.getScope());
            preparedStatement.setLong(6, user.getExpiryDate());
            preparedStatement.setString(7, user.getRefreshToken());

            preparedStatement.execute();
            return null;
        });
    }

    public Optional<SpotifyPlayingData> getPlayingData(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_playing_data WHERE telegram_user = ?"
            );
            preparedStatement.setLong(1, telegramUserId);

            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                long storedTelegramUserId = rs.getLong("telegram_user");
                String lastTrack = rs.getString("last_track_name");
                String lastTrackArtist = rs.getString("last_track_artist");
                String lastTrackUrl = rs.getString("last_track_url");
                long lastChecked = rs.getLong("last_checked");
                return Optional.of(new SpotifyPlayingData(storedTelegramUserId, lastTrack, lastTrackArtist, lastTrackUrl, lastChecked));
            } else {
                return Optional.empty();
            }
        });
    }

    public NowListeningMessage[] getNowListeningMessages(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM now_listening_messages WHERE telegram_user = ?"
            );
            preparedStatement.setLong(1, telegramUserId);

            List<NowListeningMessage> messageList = new ArrayList<>();
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                long storedTelegramUserId = rs.getLong("telegram_user");
                String inlineMessageId = rs.getString("inline_message_id");
                messageList.add(new NowListeningMessage(storedTelegramUserId, inlineMessageId));
            }
            return messageList.toArray(new NowListeningMessage[messageList.size()]);
        });
    }

    private interface ConnectionConsumer<T> {

        T consume(Connection connection) throws SQLException;

    }
}
