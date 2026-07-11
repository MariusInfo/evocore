package org.evocraft.evocore.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class WarpManager {
    private static WarpManager INSTANCE;
    public static WarpManager get() { if (INSTANCE == null) INSTANCE = new WarpManager(); return INSTANCE; }

    public static class Warp {
        public String name;
        public ItemStack icon;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
    }

    public Map<String, Warp> warps = new HashMap<>();
    private final File file = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocraft_warps.nbt");

    public WarpManager() { load(); }

    public void save() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag warpList = new ListTag();
            for (Warp w : warps.values()) {
                CompoundTag wTag = new CompoundTag();
                wTag.putString("Name", w.name);
                wTag.put("Icon", w.icon.save(new CompoundTag()));
                wTag.putString("Dimension", w.dimension);
                wTag.putDouble("X", w.x);
                wTag.putDouble("Y", w.y);
                wTag.putDouble("Z", w.z);
                wTag.putFloat("Yaw", w.yaw);
                wTag.putFloat("Pitch", w.pitch);
                warpList.add(wTag);
            }
            root.put("Warps", warpList);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void load() {
        warps.clear();
        if (!file.exists()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null) return;
            ListTag warpList = root.getList("Warps", 10);
            for (int i = 0; i < warpList.size(); i++) {
                CompoundTag wTag = warpList.getCompound(i);
                Warp w = new Warp();
                w.name = wTag.getString("Name");
                w.icon = ItemStack.of(wTag.getCompound("Icon"));
                w.dimension = wTag.getString("Dimension");
                w.x = wTag.getDouble("X");
                w.y = wTag.getDouble("Y");
                w.z = wTag.getDouble("Z");
                w.yaw = wTag.getFloat("Yaw");
                w.pitch = wTag.getFloat("Pitch");
                warps.put(w.name.toLowerCase(), w);
            }
            System.out.println("[EvoCore] Loaded " + warps.size() + " warp(s) from config.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void createWarp(String name, ServerPlayer player, ItemStack icon) {
        Warp w = new Warp();
        w.name = name;
        w.icon = icon.copy();
        w.dimension = player.level().dimension().location().toString();
        w.x = player.getX();
        w.y = player.getY();
        w.z = player.getZ();
        w.yaw = player.getYRot();
        w.pitch = player.getXRot();
        warps.put(name.toLowerCase(), w);
        save();
    }

    public void deleteWarp(String name) {
        warps.remove(name.toLowerCase());
        save();
    }
}
