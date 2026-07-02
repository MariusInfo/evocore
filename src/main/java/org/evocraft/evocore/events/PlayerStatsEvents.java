package org.evocraft.evocore.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.data.PlayerStatsManager;

@Mod.EventBusSubscriber(modid = "evocore")
public class PlayerStatsEvents {

    // --- NOU: Se declanșează INSTANT când intri pe server! ---
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Trimitem statisticile + Rank-ul din LuckPerms pe ecranul jucătorului
            PlayerStatsManager.get().syncClient(player.getUUID());
        }
    }

    // Adaugă +60 secunde la playtime o dată la 1 MINUT și trimite actualizarea pe ecran
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide()) {
            if (event.player.tickCount % 1200 == 0) {
                PlayerStatsManager.get().addPlaytime(event.player.getUUID(), 60);
            }
        }
    }

    // Înregistrează Kills și Deaths
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof ServerPlayer victim) {
            PlayerStatsManager.get().addDeath(victim.getUUID(), 1);
        }

        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            if (event.getEntity() instanceof ServerPlayer victim) {
                if (killer != victim) {
                    PlayerStatsManager.get().addKill(killer.getUUID(), 1);
                }
            }
        }
    }
}