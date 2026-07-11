package org.evocraft.evocore.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.database.DatabaseManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventLogManager {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String TABLE_NAME = "player_event_logs";

    private static EventLogManager INSTANCE;

    private final File configFile = FMLPaths.CONFIGDIR.get().resolve("evocore/eventlog.properties").toFile();
    private final Object pendingLock = new Object();
    private final Map<String, PendingEvent> pendingEvents = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EvoCore EventLog Writer");
        thread.setDaemon(true);
        return thread;
    });

    private Config config;
    private long lastFlushMs;
    private long lastPruneMs;
    private boolean running;

    public static void initialize() {
        if (INSTANCE == null) {
            INSTANCE = new EventLogManager();
        }
        INSTANCE.start();
    }

    public static EventLogManager get() {
        return INSTANCE;
    }

    public static void record(ServerPlayer player, String action, String target, BlockPos pos, String detail) {
        EventLogManager manager = INSTANCE;
        if (manager != null) {
            manager.recordInternal(player, action, target, pos, detail);
        }
    }

    public static void shutdown() {
        EventLogManager manager = INSTANCE;
        if (manager != null) {
            manager.stop();
            INSTANCE = null;
        }
    }

    private EventLogManager() {
        this.config = loadConfig();
    }

    private void start() {
        if (running) {
            return;
        }
        createTable();
        running = true;
        long now = System.currentTimeMillis();
        lastFlushMs = now;
        lastPruneMs = now;
        pruneOldAsync();
        System.out.println("[EvoCore/EventLog] Player event logging initialized. Window: "
                + config.flushIntervalSeconds + "s, retention: " + config.retentionHours + "h.");
    }

    private void stop() {
        running = false;
        flushNow();
        pruneOldNow();
        writer.shutdown();
    }

    public void onServerTick() {
        if (!running || !config.enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFlushMs >= config.flushIntervalSeconds * 1000L) {
            lastFlushMs = now;
            flushAsync();
        }

        if (now - lastPruneMs >= 60L * 60L * 1000L) {
            lastPruneMs = now;
            pruneOldAsync();
        }
    }

    public boolean isBlockBreakEnabled() {
        return config.enabled && config.logBlockBreak;
    }

    public boolean isBlockPlaceEnabled() {
        return config.enabled && config.logBlockPlace;
    }

    public boolean isBlockInteractEnabled() {
        return config.enabled && config.logBlockInteract;
    }

    public boolean isItemDropEnabled() {
        return config.enabled && config.logItemDrop;
    }

    public boolean isItemPickupEnabled() {
        return config.enabled && config.logItemPickup;
    }

    public boolean isContainerOpenEnabled() {
        return config.enabled && config.logContainerOpen;
    }

    public int defaultQueryLimit() {
        return config.defaultQueryLimit;
    }

    public int maxQueryLimit() {
        return config.maxQueryLimit;
    }

    public void flushAsync() {
        List<PendingEvent> events = drainPending();
        if (!events.isEmpty()) {
            writer.execute(() -> writeBatch(events));
        }
    }

    public void flushNow() {
        List<PendingEvent> events = drainPending();
        if (!events.isEmpty()) {
            writeBatch(events);
        }
    }

    public void pruneOldAsync() {
        writer.execute(this::pruneOldNow);
    }

    public List<Entry> getRecent(int limit) {
        return query("SELECT * FROM " + TABLE_NAME + " ORDER BY window_end DESC LIMIT ?", null, normalizeLimit(limit));
    }

    public List<Entry> getByPlayer(String playerName, int limit) {
        return query("SELECT * FROM " + TABLE_NAME + " WHERE LOWER(player_name) = LOWER(?) ORDER BY window_end DESC LIMIT ?",
                playerName, normalizeLimit(limit));
    }

    public static String formatEntry(Entry entry) {
        String time = DATE_FORMAT.format(Instant.ofEpochMilli(entry.windowEnd()));
        String action = entry.action().replace('_', ' ').toLowerCase(Locale.ROOT);
        String dimension = shortDimension(entry.dimension());
        return "[" + time + "] "
                + entry.playerName() + " "
                + action + " x" + entry.count() + " "
                + entry.target() + " @ "
                + dimension + " "
                + entry.x() + " " + entry.y() + " " + entry.z()
                + " chunk " + entry.chunkX() + "," + entry.chunkZ();
    }

    private void recordInternal(ServerPlayer player, String action, String target, BlockPos pos, String detail) {
        if (!running || !config.enabled || player == null) {
            return;
        }

        BlockPos safePos = pos != null ? pos : player.blockPosition();
        String playerName = player.getGameProfile().getName();
        String uuid = player.getUUID().toString();
        String dimension = player.level().dimension().location().toString();
        int chunkX = safePos.getX() >> 4;
        int chunkZ = safePos.getZ() >> 4;
        long now = System.currentTimeMillis();
        String safeAction = sanitize(action, 32);
        String safeTarget = sanitize(target, 191);
        String safeDetail = sanitize(detail, 512);
        String key = uuid + "|" + safeAction + "|" + safeTarget + "|" + dimension + "|" + chunkX + "|" + chunkZ;

        synchronized (pendingLock) {
            PendingEvent event = pendingEvents.get(key);
            if (event == null) {
                event = new PendingEvent(key, now, now, uuid, playerName, safeAction, safeTarget, dimension,
                        safePos.getX(), safePos.getY(), safePos.getZ(), chunkX, chunkZ, safeDetail, 0);
                pendingEvents.put(key, event);
            }
            event.count++;
            event.windowEnd = now;
            if (pendingEvents.size() >= config.maxPendingAggregates) {
                lastFlushMs = now;
                flushAsync();
            }
        }
    }

    private List<PendingEvent> drainPending() {
        synchronized (pendingLock) {
            if (pendingEvents.isEmpty()) {
                return List.of();
            }
            List<PendingEvent> events = new ArrayList<>(pendingEvents.values());
            pendingEvents.clear();
            return events;
        }
    }

    private void writeBatch(List<PendingEvent> events) {
        synchronized (DatabaseManager.get()) {
            try {
                Connection connection = DatabaseManager.get().getConnection();
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO " + TABLE_NAME
                                + " (created_at, window_start, window_end, uuid, player_name, action, target, dimension, x, y, z, chunk_x, chunk_z, count, detail)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    long createdAt = System.currentTimeMillis();
                    for (PendingEvent event : events) {
                        stmt.setLong(1, createdAt);
                        stmt.setLong(2, event.windowStart);
                        stmt.setLong(3, event.windowEnd);
                        stmt.setString(4, event.uuid);
                        stmt.setString(5, event.playerName);
                        stmt.setString(6, event.action);
                        stmt.setString(7, event.target);
                        stmt.setString(8, event.dimension);
                        stmt.setInt(9, event.x);
                        stmt.setInt(10, event.y);
                        stmt.setInt(11, event.z);
                        stmt.setInt(12, event.chunkX);
                        stmt.setInt(13, event.chunkZ);
                        stmt.setInt(14, event.count);
                        stmt.setString(15, event.detail);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (Exception e) {
                System.err.println("[EvoCore/EventLog] Failed to write player event logs.");
                e.printStackTrace();
            }
        }
    }

    private void pruneOldNow() {
        if (config.retentionHours <= 0) {
            return;
        }

        long cutoff = System.currentTimeMillis() - config.retentionHours * 60L * 60L * 1000L;
        synchronized (DatabaseManager.get()) {
            try {
                Connection connection = DatabaseManager.get().getConnection();
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM " + TABLE_NAME + " WHERE window_end < ?")) {
                    stmt.setLong(1, cutoff);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        System.out.println("[EvoCore/EventLog] Pruned " + deleted + " old event log rows.");
                    }
                }
            } catch (Exception e) {
                System.err.println("[EvoCore/EventLog] Failed to prune old event logs.");
                e.printStackTrace();
            }
        }
    }

    private List<Entry> query(String sql, String playerName, int limit) {
        List<Entry> entries = new ArrayList<>();
        synchronized (DatabaseManager.get()) {
            try {
                Connection connection = DatabaseManager.get().getConnection();
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    if (playerName == null) {
                        stmt.setInt(1, limit);
                    } else {
                        stmt.setString(1, playerName);
                        stmt.setInt(2, limit);
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entries.add(new Entry(
                                    rs.getLong("id"),
                                    rs.getLong("created_at"),
                                    rs.getLong("window_start"),
                                    rs.getLong("window_end"),
                                    rs.getString("uuid"),
                                    rs.getString("player_name"),
                                    rs.getString("action"),
                                    rs.getString("target"),
                                    rs.getString("dimension"),
                                    rs.getInt("x"),
                                    rs.getInt("y"),
                                    rs.getInt("z"),
                                    rs.getInt("chunk_x"),
                                    rs.getInt("chunk_z"),
                                    rs.getInt("count"),
                                    rs.getString("detail")
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[EvoCore/EventLog] Failed to query player event logs.");
                e.printStackTrace();
            }
        }
        return entries;
    }

    private void createTable() {
        boolean mysql = "MYSQL".equalsIgnoreCase(DatabaseManager.get().type);
        String idColumn = mysql ? "id INT AUTO_INCREMENT PRIMARY KEY" : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        String createTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + idColumn + ", "
                + "created_at BIGINT NOT NULL, "
                + "window_start BIGINT NOT NULL, "
                + "window_end BIGINT NOT NULL, "
                + "uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "action VARCHAR(32) NOT NULL, "
                + "target VARCHAR(191) NOT NULL, "
                + "dimension VARCHAR(128) NOT NULL, "
                + "x INT NOT NULL, "
                + "y INT NOT NULL, "
                + "z INT NOT NULL, "
                + "chunk_x INT NOT NULL, "
                + "chunk_z INT NOT NULL, "
                + "count INT NOT NULL, "
                + "detail TEXT"
                + ");";

        synchronized (DatabaseManager.get()) {
            try {
                Connection connection = DatabaseManager.get().getConnection();
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(createTable);
                    tryExecute(stmt, "CREATE INDEX idx_event_logs_window_end ON " + TABLE_NAME + " (window_end)");
                    tryExecute(stmt, "CREATE INDEX idx_event_logs_player ON " + TABLE_NAME + " (player_name)");
                    tryExecute(stmt, "CREATE INDEX idx_event_logs_action ON " + TABLE_NAME + " (action)");
                }
            } catch (Exception e) {
                System.err.println("[EvoCore/EventLog] Failed to create event log table.");
                e.printStackTrace();
            }
        }
    }

    private static void tryExecute(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (Exception ignored) {
        }
    }

    private Config loadConfig() {
        Properties defaults = new Properties();
        defaults.setProperty("enabled", "true");
        defaults.setProperty("flush_interval_seconds", "180");
        defaults.setProperty("retention_hours", "120");
        defaults.setProperty("default_query_limit", "20");
        defaults.setProperty("max_query_limit", "50");
        defaults.setProperty("max_pending_aggregates", "10000");
        defaults.setProperty("log_block_break", "true");
        defaults.setProperty("log_block_place", "true");
        defaults.setProperty("log_block_interact", "true");
        defaults.setProperty("log_item_drop", "true");
        defaults.setProperty("log_item_pickup", "true");
        defaults.setProperty("log_container_open", "true");

        Properties props = new Properties();
        props.putAll(defaults);
        try {
            File parent = configFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                System.err.println("[EvoCore/EventLog] Could not create config directory: " + parent.getAbsolutePath());
            }

            if (configFile.exists()) {
                try (Reader reader = new FileReader(configFile)) {
                    props.load(reader);
                }
            }

            try (Writer writer = new FileWriter(configFile)) {
                props.store(writer, "EvoCore player event log configuration");
            }
        } catch (Exception e) {
            System.err.println("[EvoCore/EventLog] Failed to load event log config. Defaults will be used.");
            e.printStackTrace();
        }

        Config loaded = new Config();
        loaded.enabled = getBoolean(props, "enabled", true);
        loaded.flushIntervalSeconds = getInt(props, "flush_interval_seconds", 180, 30, 300);
        loaded.retentionHours = getInt(props, "retention_hours", 120, 1, 8760);
        loaded.defaultQueryLimit = getInt(props, "default_query_limit", 20, 1, 100);
        loaded.maxQueryLimit = getInt(props, "max_query_limit", 50, loaded.defaultQueryLimit, 200);
        loaded.maxPendingAggregates = getInt(props, "max_pending_aggregates", 10000, 100, 200000);
        loaded.logBlockBreak = getBoolean(props, "log_block_break", true);
        loaded.logBlockPlace = getBoolean(props, "log_block_place", true);
        loaded.logBlockInteract = getBoolean(props, "log_block_interact", true);
        loaded.logItemDrop = getBoolean(props, "log_item_drop", true);
        loaded.logItemPickup = getBoolean(props, "log_item_pickup", true);
        loaded.logContainerOpen = getBoolean(props, "log_container_open", true);
        return loaded;
    }

    private int normalizeLimit(int requestedLimit) {
        return Math.max(1, Math.min(config.maxQueryLimit, requestedLimit));
    }

    private static int getInt(Properties props, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(props.getProperty(key, String.valueOf(fallback)).trim());
            if (value < min || value > max) {
                System.out.println("[EvoCore/EventLog] Invalid " + key + "=" + value + ". Using " + fallback + ".");
                return fallback;
            }
            return value;
        } catch (Exception e) {
            System.out.println("[EvoCore/EventLog] Invalid " + key + ". Using " + fallback + ".");
            return fallback;
        }
    }

    private static boolean getBoolean(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key, String.valueOf(fallback)).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        System.out.println("[EvoCore/EventLog] Invalid " + key + "=" + value + ". Using " + fallback + ".");
        return fallback;
    }

    private static String sanitize(String value, int maxLength) {
        String safe = value == null ? "unknown" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (safe.isEmpty()) {
            safe = "unknown";
        }
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static String shortDimension(String dimension) {
        if (dimension == null) {
            return "unknown";
        }
        int slash = dimension.lastIndexOf('/');
        return slash >= 0 && slash + 1 < dimension.length() ? dimension.substring(slash + 1) : dimension;
    }

    public record Entry(
            long id,
            long createdAt,
            long windowStart,
            long windowEnd,
            String uuid,
            String playerName,
            String action,
            String target,
            String dimension,
            int x,
            int y,
            int z,
            int chunkX,
            int chunkZ,
            int count,
            String detail
    ) {
    }

    private static class PendingEvent {
        private final String key;
        private final long windowStart;
        private long windowEnd;
        private final String uuid;
        private final String playerName;
        private final String action;
        private final String target;
        private final String dimension;
        private final int x;
        private final int y;
        private final int z;
        private final int chunkX;
        private final int chunkZ;
        private final String detail;
        private int count;

        private PendingEvent(String key, long windowStart, long windowEnd, String uuid, String playerName,
                             String action, String target, String dimension, int x, int y, int z,
                             int chunkX, int chunkZ, String detail, int count) {
            this.key = key;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.uuid = uuid;
            this.playerName = playerName;
            this.action = action;
            this.target = target;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.detail = detail;
            this.count = count;
        }
    }

    private static class Config {
        private boolean enabled;
        private int flushIntervalSeconds;
        private int retentionHours;
        private int defaultQueryLimit;
        private int maxQueryLimit;
        private int maxPendingAggregates;
        private boolean logBlockBreak;
        private boolean logBlockPlace;
        private boolean logBlockInteract;
        private boolean logItemDrop;
        private boolean logItemPickup;
        private boolean logContainerOpen;
    }
}
