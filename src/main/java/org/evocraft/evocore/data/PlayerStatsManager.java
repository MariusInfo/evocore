package org.evocraft.evocore.data;

import org.evocraft.evocore.database.DatabaseManager;
import org.evocraft.evocore.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlayerStatsManager {
    private static PlayerStatsManager INSTANCE;

    public static class PlayerStats {
        public String name = "Unknown";
        public double balance = 0.0;
        public int kills = 0;
        public int deaths = 0;
        public long playtimeSeconds = 0;
        public int claims = 0;
        public boolean allowTPA = true;
        public boolean allowTrade = true;
        public List<String> blockedPlayers = new ArrayList<>();

        // --- COLOANE NOI PENTRU FLY ---
        public int flyUsedSeconds = 0;
        public String flyLastDate = LocalDate.now().toString();
    }

    private record StatsSnapshot(
            UUID uuid,
            String name,
            double balance,
            int kills,
            int deaths,
            long playtimeSeconds,
            int claims,
            boolean allowTPA,
            boolean allowTrade,
            String blockedStr,
            int flyUsedSeconds,
            String flyLastDate
    ) {}

    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EvoCore PlayerStats Writer");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean shuttingDown = false;

    public static synchronized void initialize() { if (INSTANCE == null) INSTANCE = new PlayerStatsManager(); }
    public static synchronized PlayerStatsManager get() { if (INSTANCE == null) initialize(); return INSTANCE; }

    public static void shutdown() {
        PlayerStatsManager manager;
        synchronized (PlayerStatsManager.class) {
            manager = INSTANCE;
            INSTANCE = null;
        }
        if (manager != null) {
            manager.flushAndShutdown();
        }
    }

    public PlayerStatsManager() { loadAllFromDatabase(); }

    public Map<UUID, PlayerStats> getAllStats() { return statsCache; }

    public String getLuckPermsRank(UUID uuid) {
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);

            if (user != null) {
                int maxWeight = Integer.MIN_VALUE;
                String bestPrefix = null;

                for (net.luckperms.api.node.Node node : user.getNodes()) {
                    if (node instanceof net.luckperms.api.node.types.InheritanceNode) {
                        String gName = ((net.luckperms.api.node.types.InheritanceNode) node).getGroupName();
                        net.luckperms.api.model.group.Group group = lp.getGroupManager().getGroup(gName);
                        if (group != null) {
                            int weight = group.getWeight().orElse(0);
                            if (weight > maxWeight) {
                                maxWeight = weight;
                                String pfx = group.getCachedData().getMetaData().getPrefix();
                                if (pfx != null && !pfx.isEmpty()) bestPrefix = pfx;
                            }
                        }
                    }
                }
                if (bestPrefix == null) bestPrefix = user.getCachedData().getMetaData().getPrefix();
                if (bestPrefix != null && !bestPrefix.isEmpty()) return bestPrefix.replace('&', '§').replaceAll("(?i)§[k-o]", "").trim();
            }
        } catch (Throwable e) {}
        return "§7Member";
    }

    public void loadAllFromDatabase() {
        statsCache.clear();
        String query = "SELECT * FROM player_stats";

        // Punem lock (synchronized) doar cat citește la pornire
        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null) return;

                // Creăm coloanele automat dacă nu există în Baza de Date (compatibil SQLite & MySQL)
                DatabaseManager.get().ensureColumn("player_stats", "fly_used_sec", "INT DEFAULT 0");
                DatabaseManager.get().ensureColumn("player_stats", "fly_last_date", "VARCHAR(32) DEFAULT ''");

                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        if (uuidStr == null || uuidStr.isBlank()) continue;
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidStr.trim());
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        PlayerStats s = new PlayerStats();
                        String loadedName = rs.getString("name");
                        s.name = (loadedName == null || loadedName.isBlank()) ? "Unknown" : loadedName;
                        double loadedBalance = rs.getDouble("balance");
                        s.balance = (Double.isFinite(loadedBalance) && loadedBalance >= 0.0) ? loadedBalance : 0.0;
                        s.kills = Math.max(0, rs.getInt("kills"));
                        s.deaths = Math.max(0, rs.getInt("deaths"));
                        s.playtimeSeconds = Math.max(0L, rs.getLong("playtime_sec"));
                        s.claims = Math.max(0, rs.getInt("claims"));
                        s.allowTPA = rs.getBoolean("allow_tpa");
                        s.allowTrade = rs.getBoolean("allow_trade");

                        // --- ÎNCĂRCARE FLY DATA ---
                        s.flyUsedSeconds = Math.max(0, rs.getInt("fly_used_sec"));
                        String loadedFlyDate = rs.getString("fly_last_date");
                        s.flyLastDate = (loadedFlyDate == null || loadedFlyDate.isBlank())
                                ? LocalDate.now().toString()
                                : loadedFlyDate;

                        String blocked = rs.getString("blocked_players");
                        if (blocked != null && !blocked.isEmpty()) {
                            for (String entry : blocked.split(",")) {
                                String trimmed = entry.trim();
                                if (!trimmed.isEmpty()) {
                                    s.blockedPlayers.add(trimmed);
                                }
                            }
                        }
                        statsCache.put(uuid, s);
                    }
                    System.out.println("[EvoCore] Loaded statistics and economy data (including Fly) from the database.");
                }
            } catch (Throwable e) { e.printStackTrace(); }
        }
    }

    private StatsSnapshot snapshot(UUID uuid, PlayerStats s) {
        synchronized (s) {
            String safeName = (s.name == null || s.name.isBlank()) ? "Unknown" : s.name;
            if (safeName.length() > 32) safeName = safeName.substring(0, 32);
            double safeBalance = (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0;
            String safeFlyDate = (s.flyLastDate == null || s.flyLastDate.isBlank())
                    ? LocalDate.now().toString()
                    : s.flyLastDate;
            String blockedStr = String.join(",", new ArrayList<>(s.blockedPlayers));
            return new StatsSnapshot(
                    uuid,
                    safeName,
                    safeBalance,
                    Math.max(0, s.kills),
                    Math.max(0, s.deaths),
                    Math.max(0L, s.playtimeSeconds),
                    Math.max(0, s.claims),
                    s.allowTPA,
                    s.allowTrade,
                    blockedStr,
                    Math.max(0, s.flyUsedSeconds),
                    safeFlyDate
            );
        }
    }

    private void writeSnapshot(StatsSnapshot snap) {
        String mysqlQuery = "INSERT INTO player_stats (uuid, name, balance, kills, deaths, playtime_sec, claims, allow_tpa, allow_trade, blocked_players, fly_used_sec, fly_last_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name=?, balance=?, kills=?, deaths=?, playtime_sec=?, claims=?, allow_tpa=?, allow_trade=?, blocked_players=?, fly_used_sec=?, fly_last_date=?";

        String sqliteQuery = "INSERT INTO player_stats (uuid, name, balance, kills, deaths, playtime_sec, claims, allow_tpa, allow_trade, blocked_players, fly_used_sec, fly_last_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, balance=excluded.balance, kills=excluded.kills, deaths=excluded.deaths, playtime_sec=excluded.playtime_sec, claims=excluded.claims, allow_tpa=excluded.allow_tpa, allow_trade=excluded.allow_trade, blocked_players=excluded.blocked_players, fly_used_sec=excluded.fly_used_sec, fly_last_date=excluded.fly_last_date";

        int retries = 0;
        boolean saved = false;

        // Încearcă de 2 ori dacă primește Timeout noaptea
        while (!saved && retries < 2) {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null) break;
                    boolean mySql = DatabaseManager.get().isMySql();
                    String query = mySql ? mysqlQuery : sqliteQuery;
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        // SETĂRILE PENTRU INSERT
                        stmt.setString(1, snap.uuid().toString());
                        stmt.setString(2, snap.name());
                        stmt.setDouble(3, snap.balance());
                        stmt.setInt(4, snap.kills());
                        stmt.setInt(5, snap.deaths());
                        stmt.setLong(6, snap.playtimeSeconds());
                        stmt.setInt(7, snap.claims());
                        stmt.setBoolean(8, snap.allowTPA());
                        stmt.setBoolean(9, snap.allowTrade());
                        stmt.setString(10, snap.blockedStr());
                        stmt.setInt(11, snap.flyUsedSeconds());
                        stmt.setString(12, snap.flyLastDate());

                        if (mySql) {
                            // SETĂRILE PENTRU UPDATE
                            stmt.setString(13, snap.name());
                            stmt.setDouble(14, snap.balance());
                            stmt.setInt(15, snap.kills());
                            stmt.setInt(16, snap.deaths());
                            stmt.setLong(17, snap.playtimeSeconds());
                            stmt.setInt(18, snap.claims());
                            stmt.setBoolean(19, snap.allowTPA());
                            stmt.setBoolean(20, snap.allowTrade());
                            stmt.setString(21, snap.blockedStr());
                            stmt.setInt(22, snap.flyUsedSeconds());
                            stmt.setString(23, snap.flyLastDate());
                        }

                        stmt.executeUpdate();
                        saved = true; // S-a salvat perfect!
                    }
                } catch (Throwable e) {
                    retries++;
                    System.err.println("[EvoCore] Database save error (" + retries + "/2): " + e.getMessage());
                    if (retries < 2) {
                        try { Thread.sleep(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        }
    }

    public void saveToDatabase(UUID uuid) {
        if (uuid == null) return;
        PlayerStats s = getStats(uuid);
        StatsSnapshot snap = snapshot(uuid, s);
        if (shuttingDown || saveExecutor.isShutdown()) {
            writeSnapshot(snap);
            return;
        }
        try {
            saveExecutor.execute(() -> writeSnapshot(snap));
        } catch (Exception e) {
            writeSnapshot(snap);
        }
    }

    public void save() {
        for (UUID uuid : statsCache.keySet()) {
            saveToDatabase(uuid);
        }
    }

    private void flushAndShutdown() {
        shuttingDown = true;
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            saveExecutor.shutdownNow();
        }
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            writeSnapshot(snapshot(entry.getKey(), entry.getValue()));
        }
    }

    public PlayerStats getStats(UUID uuid) { return statsCache.computeIfAbsent(uuid, k -> new PlayerStats()); }

    public void syncClient(UUID uuid) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                PlayerStats stats = getStats(uuid);
                int kills;
                int deaths;
                long playtime;
                double balance;
                synchronized (stats) {
                    kills = stats.kills;
                    deaths = stats.deaths;
                    playtime = stats.playtimeSeconds;
                    balance = stats.balance;
                }
                String currentRank = getLuckPermsRank(uuid);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncStats(kills, deaths, playtime, currentRank), player);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncBalance(balance), player);
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    public void updateName(UUID uuid, String name) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.name = name; }
        saveToDatabase(uuid);
    }

    public void addKill(UUID uuid, int amount) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.kills += amount; }
        saveToDatabase(uuid);
        syncClient(uuid);
    }

    public void addDeath(UUID uuid, int amount) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.deaths += amount; }
        saveToDatabase(uuid);
        syncClient(uuid);
    }

    public void addPlaytime(UUID uuid, long seconds) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.playtimeSeconds += seconds; }
        saveToDatabase(uuid);
        syncClient(uuid);
    }

    public void updateClaims(UUID uuid, String name, int amount) {
        PlayerStats s = getStats(uuid);
        synchronized (s) {
            s.name = name;
            s.claims = Math.max(0, amount);
        }
        saveToDatabase(uuid);
    }

    public boolean canReceiveTPA(UUID target, UUID sender) {
        PlayerStats s = getStats(target);
        synchronized (s) {
            return s.allowTPA && !s.blockedPlayers.contains(sender.toString());
        }
    }

    public boolean canReceiveTrade(UUID target, UUID sender) {
        PlayerStats s = getStats(target);
        synchronized (s) {
            return s.allowTrade && !s.blockedPlayers.contains(sender.toString());
        }
    }

    public Map<String, Double> getTopData(String type) {
        Map<String, Double> map = new HashMap<>();
        for (PlayerStats s : statsCache.values()) {
            synchronized (s) {
                if (s.name == null || s.name.equals("Necunoscut") || s.name.equals("Unknown")) continue;
                if (type.equals("kills") && s.kills > 0) map.put(s.name, (double) s.kills);
                if (type.equals("decese") && s.deaths > 0) map.put(s.name, (double) s.deaths);
                if (type.equals("ore") && s.playtimeSeconds > 0) map.put(s.name, s.playtimeSeconds / 3600.0);
                if (type.equals("claimuri") && s.claims > 0) map.put(s.name, (double) s.claims);
            }
        }
        return map;
    }

    public String getNameByUUID(UUID uuid) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { return s.name; }
    }

    public void setAllowTPA(UUID uuid, boolean allow) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.allowTPA = allow; }
        saveToDatabase(uuid);
    }

    public void setAllowTrade(UUID uuid, boolean allow) {
        PlayerStats s = getStats(uuid);
        synchronized (s) { s.allowTrade = allow; }
        saveToDatabase(uuid);
    }
}
