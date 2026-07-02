package org.evocraft.evocore.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "evocore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HomeManager {
    private static HomeManager INSTANCE;
    private final Map<UUID, Map<String, HomeLocation>> playerHomes = new ConcurrentHashMap<>();

    public static final PermissionNode<Boolean> PERM_HOME_16 = new PermissionNode<>(new ResourceLocation("evocore", "homes.16"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_14 = new PermissionNode<>(new ResourceLocation("evocore", "homes.14"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_12 = new PermissionNode<>(new ResourceLocation("evocore", "homes.12"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_10 = new PermissionNode<>(new ResourceLocation("evocore", "homes.10"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_8 = new PermissionNode<>(new ResourceLocation("evocore", "homes.8"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_5 = new PermissionNode<>(new ResourceLocation("evocore", "homes.5"), PermissionTypes.BOOLEAN, (p, u, c) -> false);
    public static final PermissionNode<Boolean> PERM_HOME_3 = new PermissionNode<>(new ResourceLocation("evocore", "homes.3"), PermissionTypes.BOOLEAN, (p, u, c) -> false);

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(PERM_HOME_16, PERM_HOME_14, PERM_HOME_12, PERM_HOME_10, PERM_HOME_8, PERM_HOME_5, PERM_HOME_3);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            new Thread(() -> get().loadPlayerHomes(player.getUUID())).start();
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        get().playerHomes.remove(event.getEntity().getUUID());
    }

    public HomeManager() {}
    public static void initialize() { if (INSTANCE == null) INSTANCE = new HomeManager(); }
    public static HomeManager get() { return INSTANCE; }

    public static class HomeLocation {
        public String dimension; public double x, y, z; public float yaw, pitch;
        public HomeLocation(String dim, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dim; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
    }

    public void loadPlayerHomes(UUID uuid) {
        Map<String, HomeLocation> homes = new HashMap<>();
        String query = "SELECT * FROM player_homes WHERE uuid = ?";
        try {
            Connection conn = DatabaseManager.get().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    homes.put(rs.getString("home_name"), new HomeLocation(
                            rs.getString("dimension"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"), rs.getFloat("pitch")
                    ));
                }
                playerHomes.put(uuid, homes);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public int getMaxHomes(ServerPlayer player) {
        if (player.hasPermissions(4) || PermissionAPI.getPermission(player, PERM_HOME_16)) return 16;
        if (PermissionAPI.getPermission(player, PERM_HOME_14)) return 14;
        if (PermissionAPI.getPermission(player, PERM_HOME_12)) return 12;
        if (PermissionAPI.getPermission(player, PERM_HOME_10)) return 10;
        if (PermissionAPI.getPermission(player, PERM_HOME_8)) return 8;
        if (PermissionAPI.getPermission(player, PERM_HOME_5)) return 5;
        return 3;
    }

    public Map<String, HomeLocation> getHomes(UUID uuid) {
        return playerHomes.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    public void syncHomes(ServerPlayer player) {
        Map<String, HomeLocation> homes = getHomes(player.getUUID());
        java.util.List<String> homeNames = new java.util.ArrayList<>(homes.keySet());
        org.evocraft.evocore.network.PacketHandler.sendToPlayer(new org.evocraft.evocore.network.S2C_SyncHomesPacket(homes.size(), getMaxHomes(player), homeNames), player);
    }

    public void setHome(ServerPlayer player, String homeName) {
        Map<String, HomeLocation> homes = getHomes(player.getUUID());
        String name = homeName.toLowerCase();

        if (!homes.containsKey(name) && homes.size() >= getMaxHomes(player)) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Ai atins limita maximă de case (" + getMaxHomes(player) + ")!"));
            return;
        }

        String dim = player.level().dimension().location().toString();
        homes.put(name, new HomeLocation(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        new Thread(() -> {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM player_homes WHERE uuid = ? AND home_name = ?")) {
                    del.setString(1, player.getUUID().toString()); del.setString(2, name); del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO player_homes (uuid, home_name, dimension, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, player.getUUID().toString()); ins.setString(2, name); ins.setString(3, dim);
                    ins.setDouble(4, player.getX()); ins.setDouble(5, player.getY()); ins.setDouble(6, player.getZ());
                    ins.setFloat(7, player.getYRot()); ins.setFloat(8, player.getXRot()); ins.executeUpdate();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();

        player.sendSystemMessage(Component.literal("§a[EvoCore] Casa '§e" + homeName + "§a' a fost setată!"));
        syncHomes(player);
    }

    public void delHome(ServerPlayer player, String homeName) {
        Map<String, HomeLocation> homes = getHomes(player.getUUID());
        String name = homeName.toLowerCase();

        if (homes.remove(name) != null) {
            new Thread(() -> {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_homes WHERE uuid = ? AND home_name = ?")) {
                        stmt.setString(1, player.getUUID().toString()); stmt.setString(2, name); stmt.executeUpdate();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }).start();

            player.sendSystemMessage(Component.literal("§a[EvoCore] Casa '§e" + homeName + "§a' a fost ștearsă!"));
            syncHomes(player);
        } else {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Nu ai nicio casă cu numele '" + homeName + "'!"));
        }
    }

    public void teleportHome(ServerPlayer player, String homeName) {
        Map<String, HomeLocation> homes = getHomes(player.getUUID());
        String name = homeName.toLowerCase();

        if (!homes.containsKey(name)) {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Nu ai nicio casă cu numele '" + homeName + "'!"));
            return;
        }

        HomeLocation loc = homes.get(name);
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(loc.dimension)));

        if (level != null) {
            TeleportManager.queueTeleport(player, "Casa: " + homeName, () -> {
                player.teleportTo(level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
                player.sendSystemMessage(Component.literal("§a[EvoCore] Te-ai teleportat la '§e" + homeName + "§a'!"));
            });
        } else {
            player.sendSystemMessage(Component.literal("§c[EvoCore] Eroare! Dimensiunea casei nu mai există."));
        }
    }
}