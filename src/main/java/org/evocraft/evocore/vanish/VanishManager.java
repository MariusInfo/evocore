package org.evocraft.evocore.vanish;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.network.PacketHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class VanishManager {
    public static final Set<UUID> vanishedPlayers = new HashSet<>();
    private static final Map<UUID, GameType> previousGameModes = new HashMap<>();

    public static int toggleVanish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean isVanishing = !vanishedPlayers.contains(uuid);

        if (isVanishing) {
            vanishedPlayers.add(uuid);

            previousGameModes.putIfAbsent(uuid, player.gameMode.getGameModeForPlayer());
            player.setGameMode(GameType.SPECTATOR);

            player.sendSystemMessage(Component.literal("§a[Vanish] You are now hidden."));

            PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(true), player);
            PacketHandler.sendToAll(new PacketHandler.S2C_PlayerJoinLeave(player.getGameProfile().getName(), uuid, false));
            hideFromAllPlayers(player);
        } else {
            vanishedPlayers.remove(uuid);
            restorePreviousGameMode(player);
            player.sendSystemMessage(Component.literal("§c[Vanish] You are visible again."));

            PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(false), player);
            showToAllPlayers(player);
            PacketHandler.sendToAll(new PacketHandler.S2C_PlayerJoinLeave(player.getGameProfile().getName(), uuid, true));
        }

        PacketHandler.sendToAll(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)));
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (vanishedPlayers.contains(player.getUUID())) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    player.setGameMode(GameType.SPECTATOR);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)), sp);

            if (vanishedPlayers.contains(sp.getUUID())) {
                sp.setGameMode(GameType.SPECTATOR);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(true), sp);
            }

            scheduleHideVanishedFrom(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (vanishedPlayers.remove(player.getUUID())) {
                restorePreviousGameMode(player);
                PacketHandler.sendToAll(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)));
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            if (vanishedPlayers.contains(target.getUUID()) && !observer.getUUID().equals(target.getUUID())) {
                scheduleHideFromObserver(target, observer);
            }
        }
    }

    public static int getRealOnlineCount(net.minecraft.server.MinecraftServer server) {
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!vanishedPlayers.contains(p.getUUID())) {
                count++;
            }
        }
        return count;
    }

    public static boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public static boolean isVanished(ServerPlayer player) {
        return player != null && isVanished(player.getUUID());
    }

    private static void hideFromAllPlayers(ServerPlayer vanished) {
        for (ServerPlayer observer : vanished.getServer().getPlayerList().getPlayers()) {
            hideFromObserver(vanished, observer);
        }
    }

    private static void showToAllPlayers(ServerPlayer visible) {
        for (ServerPlayer observer : visible.getServer().getPlayerList().getPlayers()) {
            showToObserver(visible, observer);
        }
    }

    private static void scheduleHideVanishedFrom(ServerPlayer observer) {
        MinecraftServer server = observer.getServer();
        if (server == null) {
            return;
        }

        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            for (ServerPlayer vanished : server.getPlayerList().getPlayers()) {
                if (vanishedPlayers.contains(vanished.getUUID())) {
                    hideFromObserver(vanished, observer);
                }
            }
        }));
    }

    private static void scheduleHideFromObserver(ServerPlayer vanished, ServerPlayer observer) {
        MinecraftServer server = vanished.getServer();
        if (server == null) {
            hideFromObserver(vanished, observer);
            return;
        }

        server.tell(new TickTask(server.getTickCount() + 1, () -> hideFromObserver(vanished, observer)));
    }

    private static void hideFromObserver(ServerPlayer vanished, ServerPlayer observer) {
        if (observer == null || vanished == null || observer.getUUID().equals(vanished.getUUID())) {
            return;
        }

        observer.connection.send(new ClientboundPlayerInfoRemovePacket(Collections.singletonList(vanished.getUUID())));
        observer.connection.send(new ClientboundRemoveEntitiesPacket(vanished.getId()));
    }

    private static void restorePreviousGameMode(ServerPlayer player) {
        GameType previous = previousGameModes.remove(player.getUUID());
        if (previous != null && player.gameMode.getGameModeForPlayer() != previous) {
            player.setGameMode(previous);
        }
    }

    private static void showToObserver(ServerPlayer visible, ServerPlayer observer) {
        if (observer == null || visible == null || observer.getUUID().equals(visible.getUUID())) {
            return;
        }

        observer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(Collections.singletonList(visible)));

        if (observer.serverLevel() == visible.serverLevel()) {
            sendSpawnPackets(visible, observer);
        }
    }

    private static void sendSpawnPackets(ServerPlayer visible, ServerPlayer observer) {
        observer.connection.send(visible.getAddEntityPacket());

        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> entityData = visible.getEntityData().getNonDefaultValues();
        if (entityData != null && !entityData.isEmpty()) {
            observer.connection.send(new ClientboundSetEntityDataPacket(visible.getId(), entityData));
        }

        observer.connection.send(new ClientboundUpdateAttributesPacket(visible.getId(), visible.getAttributes().getSyncableAttributes()));
        observer.connection.send(new ClientboundSetEntityMotionPacket(visible));
        observer.connection.send(new ClientboundTeleportEntityPacket(visible));
        observer.connection.send(new ClientboundRotateHeadPacket(visible, (byte) ((int) (visible.getYHeadRot() * 256.0F / 360.0F))));

        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = visible.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(Pair.of(slot, stack.copy()));
            }
        }

        if (!equipment.isEmpty()) {
            observer.connection.send(new ClientboundSetEquipmentPacket(visible.getId(), equipment));
        }

        if (!visible.getPassengers().isEmpty()) {
            observer.connection.send(new ClientboundSetPassengersPacket(visible));
        }

        if (visible.isPassenger() && visible.getVehicle() != null) {
            observer.connection.send(new ClientboundSetPassengersPacket(visible.getVehicle()));
        }
    }
}
