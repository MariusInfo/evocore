package org.evocraft.evocore.database;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {
    private static DatabaseManager INSTANCE;
    private Connection connection;

    public String type = "SQLITE";
    public String host = "localhost";
    public String port = "3306";
    public String database = "evocraft";
    public String username = "root";
    public String password = "";

    private final File configFile = FMLPaths.CONFIGDIR.get().resolve("evocore/database.properties").toFile();

    public static void initialize() {
        if (INSTANCE == null) {
            INSTANCE = new DatabaseManager();
            INSTANCE.connect();
        }
    }

    public static DatabaseManager get() {
        if (INSTANCE == null) {
            initialize();
        }
        return INSTANCE;
    }

    public DatabaseManager() { loadConfig(); }

    private void loadConfig() {
        if (!configFile.getParentFile().exists()) configFile.getParentFile().mkdirs();
        Properties props = new Properties();

        if (!configFile.exists()) {
            props.setProperty("type", "SQLITE");
            props.setProperty("host", "localhost");
            props.setProperty("port", "3306");
            props.setProperty("database", "evocraft");
            props.setProperty("username", "root");
            props.setProperty("password", "");
            try (Writer writer = new FileWriter(configFile)) {
                props.store(writer, "EvoCraft Database Configuration\nType can be SQLITE or MYSQL");
            } catch (IOException e) { e.printStackTrace(); }
        } else {
            try (Reader reader = new FileReader(configFile)) {
                props.load(reader);
                this.type = props.getProperty("type", "SQLITE").toUpperCase();
                this.host = props.getProperty("host", "localhost");
                this.port = props.getProperty("port", "3306");
                this.database = props.getProperty("database", "evocraft");
                this.username = props.getProperty("username", "root");
                this.password = props.getProperty("password", "");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // SYNCHRONIZED previne eroarea de "Connection is closed"
    public synchronized void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;

            if (type.equals("MYSQL")) {
                System.out.println("[EvoCore] Attempting MariaDB/MySQL connection via " + host + "...");
                // KeepAlive forțează menținerea conexiunii active
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?autoReconnect=true&allowPublicKeyRetrieval=true&tcpKeepAlive=true";
                Class.forName("org.evocraft.evocore.repackaged.mariadb.Driver");
                connection = java.sql.DriverManager.getConnection(url, username, password);
                System.out.println("[EvoCore] CONNECTED TO MARIADB/MYSQL!");
            } else {
                System.out.println("[EvoCore] Connecting to local SQLite...");
                File dbFile = FMLPaths.CONFIGDIR.get().resolve("evocore/evodata.db").toFile();
                connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            createTables();
        } catch (Exception e) {
            System.err.println("[EvoCore] CRITICAL DATABASE CONNECTION ERROR!");
            e.printStackTrace();
        }
    }

    // SYNCHRONIZED previne crash-urile la trafic mare
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connect();
        } catch (SQLException e) { connect(); }
        return connection;
    }

    private void createTables() {
        String statsQuery = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "id INT AUTO_INCREMENT UNIQUE, " +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(32), " +
                "balance DOUBLE DEFAULT 0, " +
                "kills INT DEFAULT 0, " +
                "deaths INT DEFAULT 0, " +
                "playtime_sec BIGINT DEFAULT 0, " +
                "claims INT DEFAULT 0, " +
                "allow_tpa BOOLEAN DEFAULT TRUE, " +
                "allow_trade BOOLEAN DEFAULT TRUE, " +
                "blocked_players TEXT" +
                ");";

        String jobsQuery = "CREATE TABLE IF NOT EXISTS player_jobs (" +
                "uuid VARCHAR(36), " +
                "player_name VARCHAR(32), " +
                "job_id VARCHAR(32), " +
                "level INT DEFAULT 1, " +
                "xp DOUBLE DEFAULT 0, " +
                "is_active BOOLEAN DEFAULT TRUE, " +
                "PRIMARY KEY (uuid, job_id)" +
                ");";

        String homesQuery = "CREATE TABLE IF NOT EXISTS player_homes (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "home_name VARCHAR(50) NOT NULL, " +
                "dimension VARCHAR(100) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, " +
                "pitch FLOAT NOT NULL, " +
                "PRIMARY KEY (uuid, home_name)" +
                ");";

        // TABELUL 1 PROTECȚII: Aici se salvează chunk-urile fizice
        String claimsQuery = "CREATE TABLE IF NOT EXISTS player_claims (" +
                "chunk_key VARCHAR(100) NOT NULL PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "custom_name VARCHAR(100)" + // Adăugat custom_name
                ");";

        // TABELUL 2 PROTECȚII: Aici se salvează SLOTURILE CUMPĂRATE și PRIETENII (Trust)
        String claimSettingsQuery = "CREATE TABLE IF NOT EXISTS claim_player_settings (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "bought_slots INT DEFAULT 1, " + // Aici ține minte câte are disponibile
                "trusted_data TEXT" +
                ");";

        // TABELUL 3 PROTECȚII: Aici ținem evenimentele globale (reducerile de la admini)
        String claimEventsQuery = "CREATE TABLE IF NOT EXISTS claim_global_events (" +
                "id INT PRIMARY KEY, " +
                "stock INT DEFAULT 0, " +
                "price DOUBLE DEFAULT 0" +
                ");";

        // ADAUGAT: TABELUL 4 CHEI VOTE (Sistem Crate)
        String keysQuery = "CREATE TABLE IF NOT EXISTS player_keys (" +
                "id INT AUTO_INCREMENT UNIQUE, " +
                "username VARCHAR(32), " +
                "crate_name VARCHAR(50), " +
                "amount INT DEFAULT 0, " +
                "PRIMARY KEY (username, crate_name)" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(statsQuery);
            stmt.execute(jobsQuery);
            stmt.execute(homesQuery);
            stmt.execute(claimsQuery);
            stmt.execute(claimSettingsQuery);
            stmt.execute(claimEventsQuery);
            stmt.execute(keysQuery); // Executăm query-ul nou adăugat

            // Această linie repară problema cu Unknown column 'custom_name' pe serverele vechi
            try { stmt.execute("ALTER TABLE player_claims ADD COLUMN IF NOT EXISTS custom_name VARCHAR(100);"); } catch (SQLException ignore) {}

            System.out.println("[EvoCore] All tables were checked/created successfully in MariaDB.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }
}
