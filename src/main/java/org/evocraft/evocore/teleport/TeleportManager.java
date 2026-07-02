package org.evocraft.evocore.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "evocore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TeleportManager {
    // 60 de tick-uri = exact 3 secunde
    private static final int WARMUP_TICKS = 60;

    private static class TeleportTask {
        ServerPlayer player;
        Runnable action;
        Vec3 startPos;
        int ticksRemaining;

        public TeleportTask(ServerPlayer p, Runnable a) {
            this.player = p; this.action = a; this.startPos = p.position(); this.ticksRemaining = WARMUP_TICKS;
        }
    }

    private static final Map<UUID, TeleportTask> activeTeleports = new HashMap<>();

    public static void queueTeleport(ServerPlayer player, String destinationName, Runnable action) {
        activeTeleports.put(player.getUUID(), new TeleportTask(player, action));
        player.sendSystemMessage(Component.literal("§e[EvoCore] Teleportare spre " + destinationName + " în 3 secunde. Nu te mișca!"));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !activeTeleports.isEmpty()) {
            Iterator<Map.Entry<UUID, TeleportTask>> it = activeTeleports.entrySet().iterator();
            while (it.hasNext()) {
                TeleportTask task = it.next().getValue();

                // Daca jucatorul s-a miscat mai mult de jumatate de bloc (toleram doar rotirea camerei)
                if (task.player.position().distanceToSqr(task.startPos) > 0.25) {
                    task.player.sendSystemMessage(Component.literal("§c[EvoCore] Te-ai mișcat! Teleportarea a fost anulată."));
                    it.remove();
                    continue;
                }

                task.ticksRemaining--;
                if (task.ticksRemaining <= 0) {
                    task.action.run();
                    it.remove();
                }
            }
        }
    }
}