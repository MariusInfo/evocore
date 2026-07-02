package org.evocraft.evocore.npc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class TopManager {

    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL_TICKS = 20 * 60 * 5;

    public static final Map<String, Map<Integer, TopEntry>> topData = new HashMap<>();

    public static class TopEntry {
        public String uuid;
        public String name;
        public String displayValue;
        public TopEntry(String u, String n, String v) { uuid = u; name = n; displayValue = v; }
    }

    public static void forceUpdateNow(MinecraftServer server) {
        tickCounter = 0;
        updateDatabaseAsync(server);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter >= UPDATE_INTERVAL_TICKS || tickCounter == 20) {
            if (tickCounter >= UPDATE_INTERVAL_TICKS) tickCounter = 0;

            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                updateDatabaseAsync(server);
            }
        }
    }

    private static void updateDatabaseAsync(MinecraftServer server) {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null || conn.isClosed()) return;

                    String filter = " AND uuid != '00000000-0000-0000-0000-000000000000' AND name NOT LIKE '?%' AND name NOT LIKE '§%' ";

                    fetchCategory(conn, "bani", "SELECT uuid, name, balance AS val FROM player_stats WHERE balance > 0" + filter + "ORDER BY balance DESC LIMIT 3", false, " Lei", false);
                    fetchCategory(conn, "kills", "SELECT uuid, name, kills AS val FROM player_stats WHERE kills > 0" + filter + "ORDER BY kills DESC LIMIT 3", false, " Kills", false);
                    fetchCategory(conn, "decese", "SELECT uuid, name, deaths AS val FROM player_stats WHERE deaths > 0" + filter + "ORDER BY deaths DESC LIMIT 3", false, " Decese", false);
                    fetchCategory(conn, "ore", "SELECT uuid, name, playtime_sec AS val FROM player_stats WHERE playtime_sec > 0" + filter + "ORDER BY playtime_sec DESC LIMIT 3", true, " Ore", false);
                    fetchCategory(conn, "claims", "SELECT uuid, name, claims AS val FROM player_stats WHERE claims > 0" + filter + "ORDER BY claims DESC LIMIT 3", false, " Protecții", false);

                    String plotQuery = "SELECT s.uuid, s.name, COUNT(c.chunk_key) AS val FROM plot_claims c " +
                            "JOIN player_stats s ON c.owner_uuid = s.uuid " +
                            "WHERE s.uuid != '00000000-0000-0000-0000-000000000000' AND s.name NOT LIKE '?%' AND s.name NOT LIKE '§%' " +
                            "GROUP BY c.owner_uuid, s.uuid, s.name " +
                            "ORDER BY val DESC LIMIT 3";
                    fetchCategory(conn, "plots", plotQuery, false, " Ploturi", true);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            server.execute(() -> updateNPCsInWorld(server));
        }).start();
    }

    private static void fetchCategory(Connection conn, String category, String query, boolean isTime, String suffix, boolean dividePlot) {
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            Map<Integer, TopEntry> categoryMap = new HashMap<>();
            int rank = 1;
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String name = rs.getString("name");
                double val = rs.getDouble("val");

                // FIX MATEMATICĂ PENTRU PLOTURI
                if (dividePlot) {
                    val = val / 16.0;
                }

                String displayVal;
                if (isTime) {
                    displayVal = String.format("%.1f", val / 3600.0) + suffix;
                } else {
                    displayVal = (int)val + suffix;
                }

                if (name != null && !name.isEmpty() && !name.equals("Necunoscut")) {
                    categoryMap.put(rank, new TopEntry(uuid, name, displayVal));
                    rank++;
                }
            }
            topData.put(category, categoryMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateNPCsInWorld(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof TopNPC npc) {
                    String cat = npc.getCategory();
                    int rank = npc.getRank();

                    if (topData.containsKey(cat) && topData.get(cat).containsKey(rank)) {
                        TopEntry entry = topData.get(cat).get(rank);
                        npc.setPlayerName(entry.name);
                        npc.setDisplayValue(entry.displayValue);
                        npc.setUUIDStr(entry.uuid);
                    } else {
                        npc.setPlayerName("În curând...");
                        npc.setDisplayValue("0");
                        npc.setUUIDStr("");
                    }
                }
            }
        }
    }
}