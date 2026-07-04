package org.evocraft.evocore.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KitManager {
    private static KitManager INSTANCE;
    public static KitManager get() { if (INSTANCE == null) INSTANCE = new KitManager(); return INSTANCE; }

    public static class Kit {
        public String name;
        public String permission;
        public int cooldownHours;
        public ItemStack icon;
        public List<ItemStack> items = new ArrayList<>();
        public List<String> commands = new ArrayList<>();
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

                ListTag commandList = new ListTag();
                for (String command : kit.commands) {
                    if (command != null && !command.isBlank()) {
                        commandList.add(StringTag.valueOf(command));
                    }
                }
                kTag.put("Commands", commandList);
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
            ListTag kitList = root.getList("Kits", 10); // 10 este ID-ul pentru CompoundTag
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
                ListTag commandList = kTag.getList("Commands", 8);
                for (int j = 0; j < commandList.size(); j++) {
                    String command = normalizeCommand(commandList.getString(j));
                    if (!command.isBlank()) kit.commands.add(command);
                }
                kits.put(kit.name.toLowerCase(), kit);
            }
            System.out.println("[EvoCore] Am incarcat " + kits.size() + " kit-uri din config.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Metoda folosită de comanda de Admin
    public void createKit(String name, int cooldownHours, List<ItemStack> inventoryItems) {
        Kit kit = new Kit();
        kit.name = name;
        kit.permission = "kit." + name.toLowerCase(); // Automatizează permisiunea (ex: kit.vip)
        kit.cooldownHours = cooldownHours;

        // Caută primul item valid ca să-l pună Iconiță în meniu
        kit.icon = ItemStack.EMPTY;
        for(ItemStack stack : inventoryItems) {
            if(!stack.isEmpty()) { kit.icon = stack.copy(); break; }
        }

        kit.items.addAll(inventoryItems);
        kits.put(name.toLowerCase(), kit);
        save();
    }

    public boolean addCommand(String kitName, String command) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;

        String normalized = normalizeCommand(command);
        if (normalized.isBlank()) return false;

        kit.commands.add(normalized);
        save();
        return true;
    }

    public boolean removeCommand(String kitName, int oneBasedIndex) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null || oneBasedIndex < 1 || oneBasedIndex > kit.commands.size()) return false;

        kit.commands.remove(oneBasedIndex - 1);
        save();
        return true;
    }

    public boolean clearCommands(String kitName) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;

        kit.commands.clear();
        save();
        return true;
    }

    public int executeRewardCommands(ServerPlayer player, Kit kit) {
        if (player == null || player.getServer() == null || kit == null || kit.commands.isEmpty()) return 0;

        int executed = 0;
        CommandSourceStack source = player.getServer()
                .createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();

        for (String command : kit.commands) {
            String preparedCommand = applyPlaceholders(command, player, kit);
            if (preparedCommand.isBlank()) continue;
            player.getServer().getCommands().performPrefixedCommand(source, preparedCommand);
            executed++;
        }

        return executed;
    }

    public void deleteKit(String name) {
        kits.remove(name.toLowerCase());
        save();
    }

    private String normalizeCommand(String command) {
        if (command == null) return "";
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String applyPlaceholders(String command, ServerPlayer player, Kit kit) {
        return normalizeCommand(command)
                .replace("{player}", player.getGameProfile().getName())
                .replace("{uuid}", player.getUUID().toString())
                .replace("{kit}", kit.name);
    }
}
