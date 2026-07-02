package org.evocraft.evocore.vanish;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.network.PacketHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class VanishManager {
    public static final Set<UUID> vanishedPlayers = new HashSet<>();

    public static int toggleVanish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean isVanishing = !vanishedPlayers.contains(uuid);

        if (isVanishing) {
            vanishedPlayers.add(uuid);

            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            player.setInvisible(true);

            player.sendSystemMessage(Component.literal("§a[Vanish] Ai intrat în modul Vanish (Ascuns)."));

            PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(true), player);
            PacketHandler.sendToAll(new PacketHandler.S2C_PlayerJoinLeave(player.getGameProfile().getName(), uuid, false));
        } else {
            vanishedPlayers.remove(uuid);
            player.setInvisible(false);
            player.sendSystemMessage(Component.literal("§c[Vanish] Ai ieșit din modul Vanish (Vizibil)."));

            PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(false), player);
            PacketHandler.sendToAll(new PacketHandler.S2C_PlayerJoinLeave(player.getGameProfile().getName(), uuid, true));
        }

        PacketHandler.sendToAll(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)));
        return 1;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (vanishedPlayers.contains(player.getUUID())) {
                if (!player.isInvisible()) {
                    player.setInvisible(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)), sp);

            if (vanishedPlayers.contains(sp.getUUID())) {
                sp.setInvisible(true);
                PacketHandler.sendToPlayer(new PacketHandler.S2C_VanishHUD(true), sp);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (vanishedPlayers.remove(event.getEntity().getUUID())) {
            PacketHandler.sendToAll(new PacketHandler.S2C_SyncVanish(new ArrayList<>(vanishedPlayers)));
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
}