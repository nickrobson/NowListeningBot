package xyz.nickr.telegram.nowlistening.db;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nick Robson
 */
public class DatabaseController {

    private final String url;
    private Connection connection;

    public DatabaseController(JsonObject config) {
        JsonObject db = config.getAsJsonObject("database");

        this.url = db.getAsJsonPrimitive("url").getAsString();

        Objects.requireNonNull(this.url, "Database connection URL may not be null");

        try {
            createTables();
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to create SQL tables", ex);
        }
    }

    private interface ConnectionConsumer<T> {

        T consume(Connection connection) throws SQLException;

    }

    private <T> T withConnection(ConnectionConsumer<T> consumer) throws SQLException {
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
            try (Statement statement = connection.createStatement()) {
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
                                "last_checked INTEGER," +
                                "playing BOOLEAN" +
                                ")");
                statement.execute(
                        "CREATE TABLE IF NOT EXISTS now_listening_messages (" +
                                "id INTEGER PRIMARY KEY," +
                                "telegram_user INTEGER NOT NULL," +
                                "inline_message_id STRING UNIQUE NOT NULL," +
                                "time_added INTEGER NOT NULL" +
                                ")");
            }
            return null;
        });
    }

    public Optional<Long> getTelegramUserId(UUID uuid) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM uuids WHERE uuid = ?"
            )) {
                preparedStatement.setString(1, uuid.toString());

                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getLong("telegram_user"));
                    }
                }
                return Optional.empty();
            }
        });
    }

    public UUID getUUID(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM uuids WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
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
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM uuids WHERE uuid = ?"
            )) {
                preparedStatement.setString(1, uuid.toString());
                preparedStatement.execute();
            }
            return null;
        });
    }

    public Optional<SpotifyUser> getSpotifyUser(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_user WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);

                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(toUser(rs));
                    }
                }
                return Optional.empty();
            }
        });
    }

    public void updateSpotifyUser(SpotifyUser user) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO spotify_user " +
                            "(telegram_user, language_code, access_token, token_type, " +
                            "scope, expiry_date, refresh_token)" +
                         "VALUES (?, ?, ?, ?, ?, ?, ?)"
            )) {
                preparedStatement.setLong(1, user.getTelegramUserId());
                preparedStatement.setString(2, user.getLanguageCode());
                preparedStatement.setString(3, user.getAccessToken());
                preparedStatement.setString(4, user.getTokenType());
                preparedStatement.setString(5, user.getScope());
                preparedStatement.setLong(6, user.getExpiryDate());
                preparedStatement.setString(7, user.getRefreshToken());
                preparedStatement.execute();
            }
            return null;
        });
    }

    public void deleteSpotifyUser(long telegramUserId) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM spotify_user WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);
                preparedStatement.execute();
            }
            return null;
        });
    }

    public Set<SpotifyUser> getUsersRequiringReauthorisation() throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_user WHERE expiry_date <= ?"
            )) {
                preparedStatement.setLong(1, Instant.now().getEpochSecond());

                Set<SpotifyUser> userSet = new LinkedHashSet<>();
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        userSet.add(toUser(rs));
                    }
                }
                return Collections.unmodifiableSet(userSet);
            }
        });
    }

    public Set<SpotifyUser> getUsersWithValidAccess() throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_user WHERE expiry_date > ?"
            )) {
                preparedStatement.setLong(1, Instant.now().getEpochSecond());

                Set<SpotifyUser> userSet = new LinkedHashSet<>();
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        userSet.add(toUser(rs));
                    }
                }
                return Collections.unmodifiableSet(userSet);
            }
        });
    }

    public Set<Long> getAllUserIds() throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT telegram_user FROM spotify_user"
            )) {
                Set<Long> userSet = new LinkedHashSet<>();
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        userSet.add(rs.getLong("telegram_user"));
                    }
                }
                return Collections.unmodifiableSet(userSet);
            }
        });
    }

    private SpotifyUser toUser(ResultSet rs) throws SQLException {
        long storedTelegramUserId = rs.getLong("telegram_user");
        String languageCode = rs.getString("language_code");
        String accessToken = rs.getString("access_token");
        String tokenType = rs.getString("token_type");
        String scope = rs.getString("scope");
        long expiryDate = rs.getLong("expiry_date");
        String refreshToken = rs.getString("refresh_token");
        return new SpotifyUser(storedTelegramUserId, languageCode, accessToken, tokenType, scope, expiryDate, refreshToken);
    }

    public Optional<SpotifyPlayingData> getPlayingData(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM spotify_playing_data WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);

                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        long storedTelegramUserId = rs.getLong("telegram_user");
                        String lastTrackName = rs.getString("last_track_name");
                        String lastTrackArtist = rs.getString("last_track_artist");
                        String lastTrackUrl = rs.getString("last_track_url");
                        long lastChecked = rs.getLong("last_checked");
                        boolean playing = rs.getBoolean("playing");
                        return Optional.of(new SpotifyPlayingData(storedTelegramUserId, lastTrackName, lastTrackArtist, lastTrackUrl, lastChecked, playing));
                    } else {
                        return Optional.empty();
                    }
                }
            }
        });
    }

    public void updatePlayingData(SpotifyPlayingData playingData) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO spotify_playing_data " +
                            "(telegram_user, last_track_name, last_track_artist, " +
                            "last_track_url, last_checked, playing)" +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                preparedStatement.setLong(1, playingData.getTelegramUserId());
                preparedStatement.setString(2, playingData.getLastTrackName());
                preparedStatement.setString(3, playingData.getLastTrackArtist());
                preparedStatement.setString(4, playingData.getLastTrackUrl());
                preparedStatement.setLong(5, playingData.getLastChecked());
                preparedStatement.setBoolean(6, playingData.isPlaying());
                preparedStatement.execute();
            }
            return null;
        });
    }

    public void deletePlayingData(long telegramUserId) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM spotify_playing_data WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);
                preparedStatement.execute();
            }
            return null;
        });
    }

    public Set<NowListeningMessage> getNowListeningMessages(long telegramUserId) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM now_listening_messages WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);

                Set<NowListeningMessage> messageSet = new LinkedHashSet<>();
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    while (rs.next()) {
                        long storedTelegramUserId = rs.getLong("telegram_user");
                        String inlineMessageId = rs.getString("inline_message_id");
                        long timeAdded = rs.getLong("time_added");
                        messageSet.add(new NowListeningMessage(storedTelegramUserId, inlineMessageId, timeAdded));
                    }
                    return Collections.unmodifiableSet(messageSet);
                }
            }
        });
    }

    public void addNowListeningMessage(NowListeningMessage nowListeningMessage) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO now_listening_messages " +
                            "(telegram_user, inline_message_id, time_added) " +
                            "VALUES (?, ?, ?)"
            )) {
                preparedStatement.setLong(1, nowListeningMessage.getTelegramUserId());
                preparedStatement.setString(2, nowListeningMessage.getInlineMessageId());
                preparedStatement.setLong(3, nowListeningMessage.getTimeAdded());
                preparedStatement.execute();
            }
            return null;
        });
    }

    public void deleteNowListeningMessage(NowListeningMessage nowListeningMessage) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM now_listening_messages " +
                            "WHERE telegram_user = ? AND inline_message_id = ?"
            )) {
                preparedStatement.setLong(1, nowListeningMessage.getTelegramUserId());
                preparedStatement.setString(2, nowListeningMessage.getInlineMessageId());
                preparedStatement.execute();
            }
            return null;
        });
    }

    public void deleteAllMessages(long telegramUserId) throws SQLException {
        withConnection(connection -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    "DELETE FROM now_listening_messages WHERE telegram_user = ?"
            )) {
                preparedStatement.setLong(1, telegramUserId);
                preparedStatement.execute();
            }
            return null;
        });
    }
}
