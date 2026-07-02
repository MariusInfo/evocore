package org.evocraft.evocore.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.EvoCore;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Mod.EventBusSubscriber(modid = EvoCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnManager {

    // Am adăugat propriul LOGGER pentru această clasă
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SPAWN_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocore_spawn.json");

    // Clasă internă pentru structura fișierului JSON
    private static class SpawnData {
        String dimension = "minecraft:overworld";
        double x = 0, y = 80, z = 0;
        float yaw = 0, pitch = 0;
    }

    private static SpawnData currentSpawn = new SpawnData();

    static {
        loadSpawn();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Comanda /spawn - disponibilă pentru toți jucătorii
        dispatcher.register(Commands.literal("spawn")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    teleportToSpawn(player);
                    return 1;
                })
        );

        // Comanda /setspawn - restricționată la STAFF (nivel permisiune 2)
        dispatcher.register(Commands.literal("setspawn")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    setSpawn(player);
                    return 1;
                })
        );
    }

    public static void setSpawn(ServerPlayer player) {
        // Folosim serverLevel() în loc de level() pentru a evita warning-urile IDE-ului
        currentSpawn.dimension = player.serverLevel().dimension().location().toString();
        currentSpawn.x = player.getX();
        currentSpawn.y = player.getY();
        currentSpawn.z = player.getZ();
        currentSpawn.yaw = player.getYRot();
        currentSpawn.pitch = player.getXRot();

        saveSpawn();
        player.sendSystemMessage(Component.literal("§a[✔] Spawn-ul serverului a fost setat în locația curentă!"));
    }

    public static void teleportToSpawn(ServerPlayer player) {
        // În versiunile noi (1.20.6+), folosim ResourceLocation.parse în loc de new ResourceLocation
        ResourceLocation dimLoc = ResourceLocation.parse(currentSpawn.dimension);
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel targetLevel = player.server.getLevel(dimKey);

        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("§c[!] Eroare: Dimensiunea de spawn nu a fost găsită!"));
            return;
        }

        // Folosim TeleportManager pentru delay-ul de 3 secunde
        TeleportManager.queueTeleport(player, "Spawn", () -> {
            player.teleportTo(targetLevel, currentSpawn.x, currentSpawn.y, currentSpawn.z, currentSpawn.yaw, currentSpawn.pitch);
            player.sendSystemMessage(Component.literal("§a[✔] Bine ai revenit la Spawn!"));
        });
    }

    private static void saveSpawn() {
        try (FileWriter writer = new FileWriter(SPAWN_FILE)) {
            GSON.toJson(currentSpawn, writer);
        } catch (IOException e) {
            LOGGER.error("Nu s-a putut salva spawn-ul!", e);
        }
    }

    private static void loadSpawn() {
        if (SPAWN_FILE.exists()) {
            try (FileReader reader = new FileReader(SPAWN_FILE)) {
                currentSpawn = GSON.fromJson(reader, SpawnData.class);
            } catch (IOException e) {
                LOGGER.error("Nu s-a putut încărca spawn-ul!", e);
            }
        }
    }
}