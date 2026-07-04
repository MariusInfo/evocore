package org.evocraft.evocore.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KitManager {
    private static KitManager INSTANCE;
    public static KitManager get() { if (INSTANCE == null) INSTANCE = new KitManager(); return INSTANCE; }

    public static class Kit {
        public String name;
        public String permission;
        public int cooldownHours;
        public ItemStack icon;
        public List<ItemStack> items = new ArrayList<>();
        public List<String> crateKeys = new ArrayList<>();
    }

    public Map<String, Kit> kits = new HashMap<>();
    private final File file = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocraft_kits.nbt");

    public KitManager() { load(); }

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
        } catch (Exception e) { e.printStackTrace(); }
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

                // Smoothly migrate the temporary command-based format from early test builds.
                ListTag legacyCommands = kTag.getList("Commands", 8);
                for (int j = 0; j < legacyCommands.size(); j++) {
                    String keyName = extractCrateKeyFromLegacyCommand(legacyCommands.getString(j));
                    if (!keyName.isBlank()) keyNames.add(keyName);
                }

                kit.crateKeys.addAll(keyNames);
                kits.put(kit.name.toLowerCase(Locale.ROOT), kit);
            }
            System.out.println("[EvoCore] Loaded " + kits.size() + " kit(s) from config.");
        } catch (Exception e) { e.printStackTrace(); }
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

    private String normalizeKeyName(String keyName) {
        if (keyName == null) return "";
        return keyName.trim().toLowerCase(Locale.ROOT);
    }

    private String extractCrateKeyFromLegacyCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }

        String[] parts = normalized.split("\\s+");
        if (parts.length >= 5
                && parts[0].equalsIgnoreCase("evocrate")
                && parts[1].equalsIgnoreCase("key")
                && parts[2].equalsIgnoreCase("give")
                && parts[3].equalsIgnoreCase("{player}")) {
            return normalizeKeyName(parts[4]);
        }
        if (parts.length >= 4
                && parts[0].equalsIgnoreCase("cratekey")
                && parts[1].equalsIgnoreCase("give")
                && parts[2].equalsIgnoreCase("{player}")) {
            return normalizeKeyName(parts[3]);
        }
        return "";
    }
}
