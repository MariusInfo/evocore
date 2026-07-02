package org.evocraft.evocore.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.network.PacketHandler;

import java.time.LocalDate;

@Mod.EventBusSubscriber(modid = "evocore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlyTimeManager {

    // Nu mai avem nevoie de metodele de load/save pentru JSON, totul e in BD!

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase == TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Rulăm o dată pe secundă
        if (player.tickCount % 20 == 0) {

            // Extragem statisticile din memoria Bazei de Date
            PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(player.getUUID());
            String today = LocalDate.now().toString();

            // ========================================================
            // 1. RESETAREA AUTOMATĂ LA ZI NOUĂ (ORA 00:00)
            // ========================================================
            if (!today.equals(stats.flyLastDate)) {
                stats.flyUsedSeconds = 0;
                stats.flyLastDate = today;
                PlayerStatsManager.get().saveToDatabase(player.getUUID()); // Salvăm cu sistemul de Auto-Retry!
                player.sendSystemMessage(Component.literal("§a[!] Timpul tău de zbor a fost resetat pentru o nouă zi!"));
            }

            // ========================================================
            // 2. CRONOMETRUL CÂND ZBOARĂ
            // ========================================================
            if (player.getAbilities().mayfly) {
                int maxSeconds = getMaxFlySeconds(player);
                int usedSeconds = stats.flyUsedSeconds;

                if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) {
                    if (maxSeconds != -1) {
                        usedSeconds++;
                        stats.flyUsedSeconds = usedSeconds; // Actualizăm memoria

                        // Salvăm în BD doar din minut în minut ca să nu facem lag
                        if (usedSeconds % 60 == 0) {
                            PlayerStatsManager.get().saveToDatabase(player.getUUID());
                        }
                    }
                }

                int timeLeft = (maxSeconds == -1) ? -1 : (maxSeconds - usedSeconds);

                // A expirat timpul!
                if (maxSeconds != -1 && timeLeft <= 0) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.sendSystemMessage(Component.literal("§c✖ Timpul tău de zbor a expirat!"));
                    PlayerStatsManager.get().saveToDatabase(player.getUUID());

                    PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncFlyTime(false, 0), player);
                    return;
                }

                // Trimitem pachetul la ecran
                PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncFlyTime(true, timeLeft), player);
            }
        }
    }

    public static int getMaxFlySeconds(ServerPlayer player) {
        if (player.hasPermissions(2)) return -1; // OP = Infinit
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                if (user.getCachedData().getPermissionData().checkPermission("fly.unlimited").asBoolean()) return -1;
                if (user.getCachedData().getPermissionData().checkPermission("fly.limit.3").asBoolean()) return 3 * 3600;
                if (user.getCachedData().getPermissionData().checkPermission("fly.limit.2").asBoolean()) return 2 * 3600;
                if (user.getCachedData().getPermissionData().checkPermission("fly.limit.1").asBoolean()) return 3600;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // Funcția care citește câte secunde a consumat (citită de comanda /fly)
    public static int getUsedSeconds(ServerPlayer player) {
        return PlayerStatsManager.get().getStats(player.getUUID()).flyUsedSeconds;
    }
}