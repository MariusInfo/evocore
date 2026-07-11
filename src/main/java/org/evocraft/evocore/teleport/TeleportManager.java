package org.evocraft.evocore.teleport;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.combat.CombatLogManager;
import org.evocraft.evocore.data.FlyTimeManager;

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
    private static final Map<UUID, PlayerSyncState> lastPlayerStates = new HashMap<>();

    private static class PlayerSyncState {
        final boolean mayfly;
        final boolean flying;
        final float experienceProgress;
        final int totalExperience;
        final int experienceLevel;

        PlayerSyncState(ServerPlayer player) {
            this.mayfly = player.getAbilities().mayfly;
            this.flying = player.getAbilities().flying;
            this.experienceProgress = player.experienceProgress;
            this.totalExperience = player.totalExperience;
            this.experienceLevel = player.experienceLevel;
        }
    }

    public static void queueTeleport(ServerPlayer player, String destinationName, Runnable action) {
        activeTeleports.put(player.getUUID(), new TeleportTask(player, action));
        player.sendSystemMessage(Component.literal("§e[EvoCore] Teleporting to " + destinationName + " in 3 seconds. Do not move!"));
    }

    public static void runWithStatePreserved(ServerPlayer player, Runnable action) {
        PlayerSyncState state = new PlayerSyncState(player);
        try {
            action.run();
        } finally {
            syncPlayerAfterTeleport(player, state);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !activeTeleports.isEmpty()) {
            Iterator<Map.Entry<UUID, TeleportTask>> it = activeTeleports.entrySet().iterator();
            while (it.hasNext()) {
                TeleportTask task = it.next().getValue();

                // Daca jucatorul s-a miscat mai mult de jumatate de bloc (toleram doar rotirea camerei)
                if (task.player.position().distanceToSqr(task.startPos) > 0.25) {
                    task.player.sendSystemMessage(Component.literal("§c[EvoCore] You moved! Teleport cancelled."));
                    it.remove();
                    continue;
                }

                task.ticksRemaining--;
                if (task.ticksRemaining <= 0) {
                    runWithStatePreserved(task.player, task.action);
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer player) {
            lastPlayerStates.put(player.getUUID(), new PlayerSyncState(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSyncState state = lastPlayerStates.getOrDefault(player.getUUID(), new PlayerSyncState(player));
            syncPlayerAfterTeleport(player, state);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        activeTeleports.remove(uuid);
        lastPlayerStates.remove(uuid);
    }

    private static void syncPlayerAfterTeleport(ServerPlayer player, PlayerSyncState state) {
        applyState(player, state);

        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (onlinePlayer != null) {
                    applyState(onlinePlayer, state);
                }
            });
        }
    }

    private static void applyState(ServerPlayer player, PlayerSyncState state) {
        if (!player.hasDisconnected()) {
            restoreFlight(player, state);
            player.connection.send(new ClientboundSetExperiencePacket(state.experienceProgress, state.totalExperience, state.experienceLevel));
        }
    }

    private static void restoreFlight(ServerPlayer player, PlayerSyncState state) {
        if (!state.mayfly || !canRestoreFly(player)) {
            return;
        }

        player.getAbilities().mayfly = true;
        player.getAbilities().flying = state.flying;
        player.onUpdateAbilities();
    }

    private static boolean canRestoreFly(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        if (CombatLogManager.isInCombat(player)) {
            return false;
        }

        int maxSeconds = FlyTimeManager.getMaxFlySeconds(player);
        return maxSeconds == -1 || (maxSeconds > 0 && FlyTimeManager.getUsedSeconds(player) < maxSeconds);
    }
}
