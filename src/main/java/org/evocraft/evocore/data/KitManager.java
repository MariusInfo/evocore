package org.evocraft.evocore.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
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

    public void deleteKit(String name) {
        kits.remove(name.toLowerCase());
        save();
    }
}