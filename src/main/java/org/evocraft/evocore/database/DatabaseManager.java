package org.evocraft.evocore.database;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {
    private static final String EMBEDDED_SQLITE_RESOURCE = "/META-INF/evocore-libs/sqlite-jdbc.jar.bin";
    private static DatabaseManager INSTANCE;
    private static URLClassLoader cachedSqliteLoader;
    private static Driver cachedSqliteDriver;
    private Connection connection;

    public String type = "SQLITE";
    public String host = "localhost";
    public String port = "3306";
    public String database = "evocraft";
    public String username = "root";
    public String password = "";

    private final File configFile = FMLPaths.CONFIGDIR.get().resolve("evocore/database.properties").toFile();

    public static synchronized void initialize() {
        if (INSTANCE == null) {
            INSTANCE = new DatabaseManager();
        } else {
            INSTANCE.loadConfig();
        }
        INSTANCE.connect();
    }

    public static synchronized DatabaseManager get() {
        if (INSTANCE == null) {
            initialize();
        }
        return INSTANCE;
    }

    public DatabaseManager() { loadConfig(); }

    public boolean isSqlite() {
        return !"MYSQL".equalsIgnoreCase(type);
    }

    public boolean isMySql() {
        return "MYSQL".equalsIgnoreCase(type);
    }

    private void loadConfig() {
        if (!configFile.getParentFile().exists()) configFile.getParentFile().mkdirs();
        Properties props = new Properties();

        if (!configFile.exists()) {
            this.type = "SQLITE";
            this.host = "localhost";
            this.port = "3306";
            this.database = "evocraft";
            this.username = "root";
            this.password = "";
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
                this.type = props.getProperty("type", "SQLITE").trim().toUpperCase();
                this.host = props.getProperty("host", "localhost").trim();
                this.port = props.getProperty("port", "3306").trim();
                this.database = props.getProperty("database", "evocraft").trim();
                this.username = props.getProperty("username", "root").trim();
                this.password = props.getProperty("password", "");
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public static synchronized Connection openSqliteConnection(File dbFile) throws SQLException {
        if (dbFile == null) {
            throw new SQLException("SQLite database file cannot be null.");
        }
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        if (cachedSqliteDriver != null) {
            Connection conn = cachedSqliteDriver.connect(url, new Properties());
            if (conn != null) {
                return conn;
            }
        }

        // 1. Prefer an existing runtime SQLite driver when running on servers like Mohist
        try {
            Driver registeredDriver = DriverManager.getDriver(url);
            if (registeredDriver != null) {
                Connection conn = registeredDriver.connect(url, new Properties());
                if (conn != null) {
                    cachedSqliteDriver = registeredDriver;
                    return conn;
                }
            }
        } catch (Throwable ignored) {
            // Try Class.forName or embedded SQLite driver next
        }

        try {
            Class<?> existingClass = Class.forName("org.sqlite.JDBC");
            if (Driver.class.isAssignableFrom(existingClass)) {
                Driver existingDriver = (Driver) existingClass.getDeclaredConstructor().newInstance();
                Connection conn = existingDriver.connect(url, new Properties());
                if (conn != null) {
                    cachedSqliteDriver = existingDriver;
                    return conn;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the embedded SQLite driver for Singleplayer / Vanilla Forge
        }

        // 2. Fallback: extract embedded sqlite-jdbc.jar to config/evocore/libs and load via URLClassLoader
        try {
            Path extractedJar = ensureEmbeddedSqliteJar();
            URL jarUrl = extractedJar.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, DatabaseManager.class.getClassLoader());
            Class<?> driverClass = Class.forName("org.sqlite.JDBC", true, loader);
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            Connection conn = driver.connect(url, new Properties());
            if (conn == null) {
                try { loader.close(); } catch (IOException ignored) {}
                throw new SQLException("Embedded SQLite driver returned null connection for " + url);
            }
            cachedSqliteLoader = loader;
            cachedSqliteDriver = driver;
            return conn;
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to load embedded SQLite JDBC driver", e);
        }
    }

    private static Path ensureEmbeddedSqliteJar() throws Exception {
        Path libDir = FMLPaths.CONFIGDIR.get().resolve("evocore/libs");
        Files.createDirectories(libDir);
        Path targetJar = libDir.resolve("sqlite-jdbc.jar");

        byte[] embeddedDigest;
        long embeddedSize = 0L;
        try (InputStream in = DatabaseManager.class.getResourceAsStream(EMBEDDED_SQLITE_RESOURCE)) {
            if (in == null) {
                if (Files.isRegularFile(targetJar)) {
                    return targetJar;
                }
                throw new FileNotFoundException("Missing embedded SQLite driver resource: " + EMBEDDED_SQLITE_RESOURCE);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
                embeddedSize += read;
            }
            embeddedDigest = md.digest();
        }

        if (Files.isRegularFile(targetJar) && Files.size(targetJar) == embeddedSize) {
            if (java.util.Arrays.equals(embeddedDigest, sha256(targetJar))) {
                return targetJar;
            }
        }

        Path tempJar = Files.createTempFile(libDir, "sqlite-jdbc-", ".tmp");
        try {
            try (InputStream in = DatabaseManager.class.getResourceAsStream(EMBEDDED_SQLITE_RESOURCE);
                 OutputStream out = Files.newOutputStream(tempJar)) {
                if (in == null) {
                    throw new FileNotFoundException("Missing embedded SQLite driver resource: " + EMBEDDED_SQLITE_RESOURCE);
                }
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            Files.move(tempJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempJar);
        }
        return targetJar;
    }

    private static byte[] sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        return md.digest();
    }

    public synchronized void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                if (isSqlite() || connection.isValid(2)) return;
                try { connection.close(); } catch (SQLException ignored) {}
                connection = null;
            }

            if (isMySql()) {
                System.out.println("[EvoCore] Attempting MariaDB/MySQL connection via " + host + "...");
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?autoReconnect=true&allowPublicKeyRetrieval=true&tcpKeepAlive=true";
                Class.forName("org.evocraft.evocore.repackaged.mariadb.Driver");
                connection = DriverManager.getConnection(url, username, password);
                System.out.println("[EvoCore] CONNECTED TO MARIADB/MYSQL!");
            } else {
                System.out.println("[EvoCore] Connecting to local SQLite...");
                File dbFile = FMLPaths.CONFIGDIR.get().resolve("evocore/evodata.db").toFile();
                connection = openSqliteConnection(dbFile);
                configureSqlitePragmas(connection);
            }
            createTables();
        } catch (Exception e) {
            System.err.println("[EvoCore] CRITICAL DATABASE CONNECTION ERROR!");
            e.printStackTrace();
        }
    }

    private void configureSqlitePragmas(Connection conn) {
        if (conn == null) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout = 5000;");
            try { stmt.execute("PRAGMA journal_mode = WAL;"); } catch (SQLException ignored) {}
        } catch (SQLException ignored) {
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || (isMySql() && !connection.isValid(2))) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    public synchronized boolean ensureColumn(String table, String column, String columnDef) {
        if (table == null || column == null || columnDef == null
                || !table.matches("^[A-Za-z0-9_]+$") || !column.matches("^[A-Za-z0-9_]+$")) {
            return false;
        }
        Connection conn = getConnection();
        if (conn == null) return false;
        try {
            boolean exists = false;
            if (isSqlite()) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
                    while (rs.next()) {
                        String colName = rs.getString("name");
                        if (column.equalsIgnoreCase(colName)) {
                            exists = true;
                            break;
                        }
                    }
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("SHOW COLUMNS FROM `" + table + "` LIKE ?")) {
                    ps.setString(1, column);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }
            }
            if (!exists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDef);
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("[EvoCore] Failed ensuring column " + table + "." + column + ": " + e.getMessage());
            return false;
        }
    }

    private void createTables() {
        boolean isSqlite = isSqlite();

        // SQLite nu suport AUTO_INCREMENT, ci AUTOINCREMENT pentru INTEGER PRIMARY KEY.
        String idStats = isSqlite ? "id INTEGER UNIQUE" : "id INT AUTO_INCREMENT UNIQUE";
        String idKeys = isSqlite ? "id INTEGER UNIQUE" : "id INT AUTO_INCREMENT UNIQUE";

        String statsQuery = "CREATE TABLE IF NOT EXISTS player_stats (" +
                idStats + ", " +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(32), " +
                "balance DOUBLE DEFAULT 0, " +
                "kills INT DEFAULT 0, " +
                "deaths INT DEFAULT 0, " +
                "playtime_sec BIGINT DEFAULT 0, " +
                "claims INT DEFAULT 0, " +
                "allow_tpa BOOLEAN DEFAULT TRUE, " +
                "allow_trade BOOLEAN DEFAULT TRUE, " +
                "blocked_players TEXT, " +
                "fly_used_sec INT DEFAULT 0, " +
                "fly_last_date VARCHAR(32) DEFAULT ''" +
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

        String claimsQuery = "CREATE TABLE IF NOT EXISTS player_claims (" +
                "chunk_key VARCHAR(100) NOT NULL PRIMARY KEY, " +
                "owner_uuid VARCHAR(36) NOT NULL, " +
                "custom_name VARCHAR(100)" +
                ");";

        String claimSettingsQuery = "CREATE TABLE IF NOT EXISTS claim_player_settings (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "bought_slots INT DEFAULT 1, " +
                "trusted_data TEXT" +
                ");";

        String claimEventsQuery = "CREATE TABLE IF NOT EXISTS claim_global_events (" +
                "id INT PRIMARY KEY, " +
                "stock INT DEFAULT 0, " +
                "price DOUBLE DEFAULT 0" +
                ");";

        String keysQuery = "CREATE TABLE IF NOT EXISTS player_keys (" +
                idKeys + ", " +
                "username VARCHAR(32), " +
                "crate_name VARCHAR(50), " +
                "amount INT DEFAULT 0, " +
                "PRIMARY KEY (username, crate_name)" +
                ");";

        Connection conn = connection;
        if (conn == null) return;
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(statsQuery);
            stmt.execute(jobsQuery);
            stmt.execute(homesQuery);
            stmt.execute(claimsQuery);
            stmt.execute(claimSettingsQuery);
            stmt.execute(claimEventsQuery);
            stmt.execute(keysQuery);

            ensureColumn("player_stats", "fly_used_sec", "INT DEFAULT 0");
            ensureColumn("player_stats", "fly_last_date", "VARCHAR(32) DEFAULT ''");
            ensureColumn("player_claims", "custom_name", "VARCHAR(100)");

            System.out.println("[EvoCore] All tables were checked/created successfully in " + type + ".");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection = null;
        }
    }
}
