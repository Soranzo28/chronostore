package dev.soranzo;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Database {

    private static Database instance;
    private final Connection connection;

    private Database(File dataFolder) throws SQLException {
        dataFolder.mkdirs();
        String url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
        connection = DriverManager.getConnection(url);
        initialize();
    }

    public static Database getInstance(File dataFolder) throws SQLException {
        if (instance == null) {
            instance = new Database(dataFolder);
        }
        return instance;
    }

    public static Database getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Database não foi inicializada");
        }
        return instance;
    }

    private void initialize() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS config (
                    id INTEGER PRIMARY KEY,
                    last_reset INTEGER NOT NULL,
                    paused INTEGER DEFAULT 0,
                    last_pause INTEGER
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    time_limit INTEGER DEFAULT 7200, 
                    time_tbsp INTEGER DEFAULT 0,
                    time_played_today INTEGER DEFAULT 0,
                    monitored INTEGER DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    date_in INTEGER NOT NULL,
                    date_out INTEGER,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
                );
            """);

            stmt.execute("""
                INSERT OR IGNORE INTO config (id, last_reset)
                VALUES (1, strftime('%s', 'now'));
            """);
        }
    }

    public void addPlayer(UUID uuid, String name) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("INSERT INTO players (uuid, name, monitored) VALUES (?, ?, 0)");
        stmt.setString(1, uuid.toString());
        stmt.setString(2, name);
        stmt.executeUpdate();
    }

    public void monitorPlayer(UUID uuid, boolean value) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE players SET monitored = ? WHERE uuid = ?");
        stmt.setInt(1, value ? 1 : 0);
        stmt.setString(2, uuid.toString());
        stmt.executeUpdate();
    }

    public void setTbsp(UUID uuid, long time) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE players SET time_tbsp = ? WHERE uuid = ?");
        stmt.setLong(1, time);
        stmt.setString(2, uuid.toString());
        stmt.executeUpdate();
    }

    public void beginSession(UUID uuid, long now) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("INSERT INTO sessions (player_uuid, date_in) VALUES (?, ?)");
        stmt.setString(1, uuid.toString());
        stmt.setLong(2, now);
        stmt.executeUpdate();

    }

    public void endSession(UUID uuid) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE sessions SET date_out = strftime('%s', 'now') WHERE player_uuid = ? AND date_out IS NULL");
        stmt.setString(1, uuid.toString());
        stmt.executeUpdate();
    }

    public PlayerData getPlayerData(UUID uuid) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("SELECT * FROM players WHERE uuid = ?");
        stmt.setString(1, uuid.toString());

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return new PlayerData(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("name"),
                    rs.getInt("time_limit"),
                    rs.getLong("time_tbsp"),
                    rs.getInt("time_played_today"),
                    rs.getInt("monitored") == 1
            );
        }

        return null;
    }

    public void addTimePlayed(UUID uuid, long seconds) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
                "UPDATE players SET time_played_today = time_played_today + ? WHERE uuid = ?"
        );
        stmt.setLong(1, seconds);
        stmt.setString(2, uuid.toString());
        stmt.executeUpdate();
    }

    public void resetOneTimePlayed(UUID uuid) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE players SET time_played_today = 0 WHERE uuid = ?");
        stmt.setString(1, uuid.toString());
        stmt.executeUpdate();
    }

    public void resetAllTimePlayed() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE players SET time_played_today = 0");
        stmt.executeUpdate();
    }

    public long getLastReset() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("SELECT last_reset FROM config WHERE id = 1");
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getLong("last_reset");
        }

        return 0;
    }

    public boolean isPaused() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("SELECT paused FROM config WHERE id = 1");
        ResultSet rs = stmt.executeQuery();
        return rs.next() && rs.getInt("paused") == 1;
    }

    public void setPaused(boolean value) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE config SET paused = ? WHERE id = 1");
        stmt.setInt(1, value ? 1 : 0);
        stmt.executeUpdate();
    }

    public void setLastPause(long epoch_pause_moment) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE config SET last_pause = ? WHERE id = 1");
        stmt.setLong(1, epoch_pause_moment);
        stmt.executeUpdate();
    }

    public long getLastPause() throws  SQLException {
        PreparedStatement stmt = connection.prepareStatement("SELECT last_pause FROM config WHERE id = 1");
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return rs.getLong("last_pause");
        return 0;
    }

    public UUID findUUIDByName(String name) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(
            "SELECT uuid FROM players WHERE LOWER(name) = LOWER(?)");
        stmt.setString(1, name);
        ResultSet rs = stmt.executeQuery();
        return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
    }

    public void setTimeLimit(UUID uuid, int seconds) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE players SET time_limit = ? WHERE uuid = ?");
        stmt.setInt(1, seconds);
        stmt.setString(2, uuid.toString());
        stmt.executeUpdate();
    }

    public void setLastReset() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("UPDATE config SET last_reset = strftime('%s', 'now') WHERE id = 1");
        stmt.executeUpdate();
    }

    public void closeOrphanedSessions() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("UPDATE sessions SET date_out = strftime('%s','now') WHERE date_out IS NULL");
        }
    }

    public Map<String, Long> getAllTotalTimePlayed() throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("""
            SELECT p.name, SUM(COALESCE(s.date_out, strftime('%s','now')) - s.date_in) AS total
            FROM players p
            INNER JOIN sessions s ON p.uuid = s.player_uuid
            GROUP BY p.uuid, p.name
            HAVING total > 0
            ORDER BY total DESC
            LIMIT 10
        """);
        ResultSet rs = stmt.executeQuery();
        Map<String, Long> result = new LinkedHashMap<>();
        while (rs.next()) {
            result.put(rs.getString("name"), rs.getLong("total"));
        }
        return result;
    }
}