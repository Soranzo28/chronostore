package dev.soranzo;

import java.io.File;
import java.sql.*;

public class Database {

    private static Database instance;
    private Connection connection;

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
                    last_reset INTEGER NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    time_limit INTEGER DEFAULT 120, 
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
}