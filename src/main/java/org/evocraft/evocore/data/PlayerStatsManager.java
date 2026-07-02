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

public class PlayerStatsManager {
    private static PlayerStatsManager INSTANCE;

    public static class PlayerStats {
        public String name = "Necunoscut";
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

    private final Map<UUID, PlayerStats> statsCache = new HashMap<>();

    public static void initialize() { if (INSTANCE == null) INSTANCE = new PlayerStatsManager(); }
    public static PlayerStatsManager get() { if (INSTANCE == null) initialize(); return INSTANCE; }

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
        } catch (Exception e) {}
        return "§7Membru";
    }

    public void loadAllFromDatabase() {
        statsCache.clear();
        String query = "SELECT * FROM player_stats";

        // Punem lock (synchronized) doar cat citește la pornire
        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null) return;

                // Creăm coloanele automat dacă nu există în Baza de Date
                try (PreparedStatement stCheck = conn.prepareStatement("ALTER TABLE player_stats ADD COLUMN IF NOT EXISTS fly_used_sec INT DEFAULT 0, ADD COLUMN IF NOT EXISTS fly_last_date VARCHAR(32) DEFAULT ''")) {
                    stCheck.execute();
                } catch (Exception ignored) {} // Ignorăm eroarea dacă există deja

                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        PlayerStats s = new PlayerStats();
                        s.name = rs.getString("name");
                        s.balance = rs.getDouble("balance");
                        s.kills = rs.getInt("kills");
                        s.deaths = rs.getInt("deaths");
                        s.playtimeSeconds = rs.getLong("playtime_sec");
                        s.claims = rs.getInt("claims");
                        s.allowTPA = rs.getBoolean("allow_tpa");
                        s.allowTrade = rs.getBoolean("allow_trade");

                        // --- ÎNCĂRCARE FLY DATA ---
                        s.flyUsedSeconds = rs.getInt("fly_used_sec");
                        s.flyLastDate = rs.getString("fly_last_date");

                        String blocked = rs.getString("blocked_players");
                        if (blocked != null && !blocked.isEmpty()) {
                            s.blockedPlayers = new ArrayList<>(Arrays.asList(blocked.split(",")));
                        }
                        statsCache.put(uuid, s);
                    }
                    System.out.println("[EvoCore] Am încărcat statisticile și economia (inclusiv Fly) din Baza de Date.");
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public void saveToDatabase(UUID uuid) {
        PlayerStats s = getStats(uuid);

        // Copiem variabilele local ca să fie sigure pentru transferul pe noul Thread
        final String name = s.name;
        final double balance = s.balance;
        final int kills = s.kills;
        final int deaths = s.deaths;
        final long playtimeSeconds = s.playtimeSeconds;
        final int claims = s.claims;
        final boolean allowTPA = s.allowTPA;
        final boolean allowTrade = s.allowTrade;
        final String blockedStr = String.join(",", s.blockedPlayers);

        // NOILE VARIABILE DE FLY
        final int flyUsed = s.flyUsedSeconds;
        final String flyDate = s.flyLastDate;

        // MUTAT PE FUNDAL! Cu sistem de Auto-Retry integrat.
        new Thread(() -> {
            String query = "INSERT INTO player_stats (uuid, name, balance, kills, deaths, playtime_sec, claims, allow_tpa, allow_trade, blocked_players, fly_used_sec, fly_last_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name=?, balance=?, kills=?, deaths=?, playtime_sec=?, claims=?, allow_tpa=?, allow_trade=?, blocked_players=?, fly_used_sec=?, fly_last_date=?";

            int retries = 0;
            boolean saved = false;

            // Încearcă de 2 ori dacă primește Timeout noaptea
            while (!saved && retries < 2) {
                synchronized (DatabaseManager.get()) {
                    try {
                        Connection conn = DatabaseManager.get().getConnection();
                        if (conn == null) break;
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            // SETĂRILE PENTRU INSERT
                            stmt.setString(1, uuid.toString());
                            stmt.setString(2, name);
                            stmt.setDouble(3, balance);
                            stmt.setInt(4, kills);
                            stmt.setInt(5, deaths);
                            stmt.setLong(6, playtimeSeconds);
                            stmt.setInt(7, claims);
                            stmt.setBoolean(8, allowTPA);
                            stmt.setBoolean(9, allowTrade);
                            stmt.setString(10, blockedStr);
                            stmt.setInt(11, flyUsed);
                            stmt.setString(12, flyDate);

                            // SETĂRILE PENTRU UPDATE
                            stmt.setString(13, name);
                            stmt.setDouble(14, balance);
                            stmt.setInt(15, kills);
                            stmt.setInt(16, deaths);
                            stmt.setLong(17, playtimeSeconds);
                            stmt.setInt(18, claims);
                            stmt.setBoolean(19, allowTPA);
                            stmt.setBoolean(20, allowTrade);
                            stmt.setString(21, blockedStr);
                            stmt.setInt(22, flyUsed);
                            stmt.setString(23, flyDate);

                            stmt.executeUpdate();
                            saved = true; // S-a salvat perfect!
                        }
                    } catch (Exception e) {
                        retries++;
                        System.err.println("[EvoCore] Timeout Bază de Date! Reîncerc conexiunea... (" + retries + "/2)");
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
    }

    public void save() { for (UUID uuid : statsCache.keySet()) saveToDatabase(uuid); }

    public PlayerStats getStats(UUID uuid) { return statsCache.computeIfAbsent(uuid, k -> new PlayerStats()); }

    public void syncClient(UUID uuid) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                PlayerStats stats = getStats(uuid);
                String currentRank = getLuckPermsRank(uuid);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncStats(stats.kills, stats.deaths, stats.playtimeSeconds, currentRank), player);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncBalance(stats.balance), player);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void updateName(UUID uuid, String name) { getStats(uuid).name = name; saveToDatabase(uuid); }
    public void addKill(UUID uuid, int amount) { getStats(uuid).kills += amount; saveToDatabase(uuid); syncClient(uuid); }
    public void addDeath(UUID uuid, int amount) { getStats(uuid).deaths += amount; saveToDatabase(uuid); syncClient(uuid); }
    public void addPlaytime(UUID uuid, long seconds) { getStats(uuid).playtimeSeconds += seconds; saveToDatabase(uuid); syncClient(uuid); }
    public void updateClaims(UUID uuid, String name, int amount) { PlayerStats s = getStats(uuid); s.name = name; s.claims = amount; saveToDatabase(uuid); }

    public boolean canReceiveTPA(UUID target, UUID sender) {
        PlayerStats s = getStats(target); return s.allowTPA && !s.blockedPlayers.contains(sender.toString());
    }

    public boolean canReceiveTrade(UUID target, UUID sender) {
        PlayerStats s = getStats(target); return s.allowTrade && !s.blockedPlayers.contains(sender.toString());
    }

    public Map<String, Double> getTopData(String type) {
        Map<String, Double> map = new HashMap<>();
        for (PlayerStats s : statsCache.values()) {
            if (s.name == null || s.name.equals("Necunoscut")) continue;
            if (type.equals("kills") && s.kills > 0) map.put(s.name, (double) s.kills);
            if (type.equals("decese") && s.deaths > 0) map.put(s.name, (double) s.deaths);
            if (type.equals("ore") && s.playtimeSeconds > 0) map.put(s.name, s.playtimeSeconds / 3600.0);
            if (type.equals("claimuri") && s.claims > 0) map.put(s.name, (double) s.claims);
        }
        return map;
    }
    public String getNameByUUID(UUID uuid) { return getStats(uuid).name; }
    public void setAllowTPA(UUID uuid, boolean allow) { PlayerStats s = getStats(uuid); s.allowTPA = allow; saveToDatabase(uuid); }
    public void setAllowTrade(UUID uuid, boolean allow) { PlayerStats s = getStats(uuid); s.allowTrade = allow; saveToDatabase(uuid); }
}