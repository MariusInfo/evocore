package org.evocraft.evokits.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.database.DatabaseManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {
    private static KitManager INSTANCE;

    public static KitManager get() {
        if (INSTANCE == null) INSTANCE = new KitManager();
        return INSTANCE;
    }

    public static class Kit {
        public String name;
        public String permission;
        public int cooldownHours;
        public ItemStack icon;
        public List<ItemStack> items = new ArrayList<>();
        public List<String> crateKeys = new ArrayList<>();
    }

    public final Map<String, Kit> kits = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldownCache = new ConcurrentHashMap<>();
    private final File file = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocraft_kits.nbt");

    public KitManager() {
        load();
        initializeDB();
    }

    public void initializeDB() {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_kit_cooldowns (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "kit_name VARCHAR(64) NOT NULL, " +
                                "last_claimed BIGINT NOT NULL, " +
                                "PRIMARY KEY (uuid, kit_name))");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void save() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag kitList = new ListTag();
            for (Kit kit : kits.values()) {
                CompoundTag kTag = new CompoundTag();
                kTag.putString("Name", kit.name);
                kTag.putString("Permission", kit.permission);
                kTag.putInt("Cooldown", kit.cooldownHours);
                kTag.put("Icon", kit.icon.save(new CompoundTag()));

                ListTag itemList = new ListTag();
                for (ItemStack item : kit.items) {
                    itemList.add(item.save(new CompoundTag()));
                }
                kTag.put("Items", itemList);

                ListTag keyList = new ListTag();
                for (String keyName : kit.crateKeys) {
                    String normalized = normalizeKeyName(keyName);
                    if (!normalized.isBlank()) {
                        keyList.add(StringTag.valueOf(normalized));
                    }
                }
                kTag.put("CrateKeys", keyList);
                kitList.add(kTag);
            }
            root.put("Kits", kitList);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void load() {
        kits.clear();
        if (!file.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null) return;
            ListTag kitList = root.getList("Kits", 10);
            for (int i = 0; i < kitList.size(); i++) {
                CompoundTag kTag = kitList.getCompound(i);
                Kit kit = new Kit();
                kit.name = kTag.getString("Name");
                kit.permission = kTag.getString("Permission");
                kit.cooldownHours = kTag.getInt("Cooldown");
                kit.icon = ItemStack.of(kTag.getCompound("Icon"));

                ListTag itemList = kTag.getList("Items", 10);
                for (int j = 0; j < itemList.size(); j++) {
                    kit.items.add(ItemStack.of(itemList.getCompound(j)));
                }

                Set<String> keyNames = new LinkedHashSet<>();
                ListTag keyList = kTag.getList("CrateKeys", 8);
                for (int j = 0; j < keyList.size(); j++) {
                    String keyName = normalizeKeyName(keyList.getString(j));
                    if (!keyName.isBlank()) keyNames.add(keyName);
                }

                kit.crateKeys.addAll(keyNames);
                kits.put(kit.name.toLowerCase(Locale.ROOT), kit);
            }
            System.out.println("[EvoKits] Loaded " + kits.size() + " kit(s) from config.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createKit(String name, int cooldownHours, List<ItemStack> inventoryItems) {
        Kit kit = new Kit();
        kit.name = name;
        kit.permission = "kit." + name.toLowerCase(Locale.ROOT);
        kit.cooldownHours = cooldownHours;

        kit.icon = ItemStack.EMPTY;
        for (ItemStack stack : inventoryItems) {
            if (!stack.isEmpty()) {
                kit.icon = stack.copy();
                break;
            }
        }

        kit.items.addAll(inventoryItems);
        kits.put(name.toLowerCase(Locale.ROOT), kit);
        save();
    }

    public boolean addCrateKey(String keyName, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase(Locale.ROOT));
        if (kit == null) return false;

        String normalizedKey = normalizeKeyName(keyName);
        if (normalizedKey.isBlank()) return false;

        if (!kit.crateKeys.contains(normalizedKey)) {
            kit.crateKeys.add(normalizedKey);
            save();
        }
        return true;
    }

    public int grantCrateKeys(ServerPlayer player, Kit kit) {
        if (player == null || kit == null || kit.crateKeys.isEmpty()) return 0;

        int granted = 0;
        for (String keyName : kit.crateKeys) {
            String normalizedKey = normalizeKeyName(keyName);
            if (normalizedKey.isBlank()) continue;
            CrateKeyManager.get().addKeys(player.getGameProfile().getName(), normalizedKey, 1);
            granted++;
        }
        return granted;
    }

    public void deleteKit(String name) {
        kits.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    public long getRemainingCooldownSeconds(UUID uuid, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase(Locale.ROOT));
        if (kit == null || kit.cooldownHours <= 0) return 0;

        long lastClaim = getCooldownFromCacheOrDB(uuid, kit.name.toLowerCase(Locale.ROOT));
        if (lastClaim <= 0) return 0;

        long cooldownMillis = (long) kit.cooldownHours * 3600L * 1000L;
        long elapsed = System.currentTimeMillis() - lastClaim;
        if (elapsed >= cooldownMillis) return 0;

        return (cooldownMillis - elapsed) / 1000L;
    }

    private long getCooldownFromCacheOrDB(UUID uuid, String kitKey) {
        Map<String, Long> playerMap = cooldownCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (playerMap.containsKey(kitKey)) {
            return playerMap.get(kitKey);
        }

        try {
            Connection conn = DatabaseManager.get().getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT last_claimed FROM player_kit_cooldowns WHERE uuid = ? AND kit_name = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kitKey);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    long val = rs.getLong("last_claimed");
                    playerMap.put(kitKey, val);
                    return val;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        playerMap.put(kitKey, 0L);
        return 0L;
    }

    public boolean claimKit(ServerPlayer player, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase(Locale.ROOT));
        if (kit == null) {
            player.sendSystemMessage(Component.literal("§c✖ Kit §e" + kitName + " §cdoes not exist!"));
            return false;
        }

        if (!hasPermission(player, kit.permission) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("§c✖ You do not have permission to claim kit §e" + kit.name + "§c!"));
            return false;
        }

        long remainingSec = getRemainingCooldownSeconds(player.getUUID(), kit.name);
        if (remainingSec > 0 && !player.hasPermissions(2)) {
            long hours = remainingSec / 3600;
            long minutes = (remainingSec % 3600) / 60;
            long seconds = remainingSec % 60;
            player.sendSystemMessage(Component.literal("§c✖ You must wait §e" + hours + "h " + minutes + "m " + seconds + "s §cbefore claiming this kit again!"));
            return false;
        }

        for (ItemStack stack : kit.items) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }

        int keysGranted = grantCrateKeys(player, kit);

        long now = System.currentTimeMillis();
        cooldownCache.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).put(kit.name.toLowerCase(Locale.ROOT), now);

        new Thread(() -> {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                String query = "MYSQL".equalsIgnoreCase(DatabaseManager.get().type)
                        ? "INSERT INTO player_kit_cooldowns (uuid, kit_name, last_claimed) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE last_claimed = ?"
                        : "INSERT INTO player_kit_cooldowns (uuid, kit_name, last_claimed) VALUES (?, ?, ?) ON CONFLICT(uuid, kit_name) DO UPDATE SET last_claimed = excluded.last_claimed";
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setString(1, player.getUUID().toString());
                    ps.setString(2, kit.name.toLowerCase(Locale.ROOT));
                    ps.setLong(3, now);
                    if ("MYSQL".equalsIgnoreCase(DatabaseManager.get().type)) {
                        ps.setLong(4, now);
                    }
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        player.sendSystemMessage(Component.literal("§a✔ Successfully claimed kit §l" + kit.name + "§a!"));
        if (keysGranted > 0) {
            player.sendSystemMessage(Component.literal("§6★ Received §e" + keysGranted + " §6crate key(s)!"));
        }
        return true;
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        if (player.hasPermissions(2)) return true;
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String normalizeKeyName(String keyName) {
        if (keyName == null) return "";
        return keyName.trim().toLowerCase(Locale.ROOT);
    }
}
