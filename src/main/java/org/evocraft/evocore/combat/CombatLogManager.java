package org.evocraft.evocore.combat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.network.PacketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class CombatLogManager {

    private static final Map<UUID, Long> combatMap = new HashMap<>();
    // AICI AM MODIFICAT: 30.000 milisecunde = 30 Secunde
    private static final int COMBAT_TIME_MS = 30000;

    // ========================================================
    // FILTRUL MAGIC: Doar Jucători și Boși (peste 50 HP)
    // ========================================================
    private static boolean isValidCombatTarget(LivingEntity entity) {
        if (entity instanceof ServerPlayer) return true; // Jucătorii se pun mereu
        if (entity.getMaxHealth() >= 50.0f) return true; // Boșii (Wither, Cataclysm etc.)
        return false;
    }

    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity victim = event.getEntity();
        net.minecraft.world.entity.Entity attackerEntity = event.getSource().getEntity();

        // 1. Jucătorul ia damage
        if (victim instanceof ServerPlayer targetPlayer) {
            if (attackerEntity instanceof LivingEntity attacker && isValidCombatTarget(attacker)) {
                setInCombat(targetPlayer);
            }
        }

        // 2. Jucătorul dă damage
        if (attackerEntity instanceof ServerPlayer attackerPlayer) {
            if (isValidCombatTarget(victim)) {
                setInCombat(attackerPlayer);
            }
        }
    }

    private static void setInCombat(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean wasInCombat = isInCombat(player);
        long expirationTime = System.currentTimeMillis() + COMBAT_TIME_MS;

        combatMap.put(uuid, expirationTime);

        if (player.getAbilities().mayfly && !player.isCreative()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.sendSystemMessage(Component.literal("§c⚠ You entered combat! Fly was disabled automatically!"));
        } else if (!wasInCombat) {
            player.sendSystemMessage(Component.literal("§c[⚔] You are in COMBAT! Do not leave the server!"));
        }

        PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncCombat(true, COMBAT_TIME_MS), player);
    }

    public static boolean isInCombat(ServerPlayer player) {
        if (combatMap.containsKey(player.getUUID())) {
            long expiration = combatMap.get(player.getUUID());
            if (System.currentTimeMillis() < expiration) {
                return true;
            } else {
                removeCombat(player);
                return false;
            }
        }
        return false;
    }

    public static void removeCombat(ServerPlayer player) {
        combatMap.remove(player.getUUID());
        PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncCombat(false, 0), player);
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();

        if (victim instanceof ServerPlayer deadPlayer) {
            if (combatMap.containsKey(deadPlayer.getUUID())) {
                removeCombat(deadPlayer);
                deadPlayer.sendSystemMessage(Component.literal("§a[✔] You died and left combat."));
            }
        }

        ServerPlayer attacker = null;
        if (event.getSource().getEntity() instanceof ServerPlayer directAttacker) {
            attacker = directAttacker;
        } else if (victim.getLastHurtByMob() instanceof ServerPlayer lastAttacker) {
            attacker = lastAttacker;
        }

        if (attacker != null && combatMap.containsKey(attacker.getUUID())) {
            // Ieși instant din combat dacă ținta moare (ȘI e o țintă validă, gen Player sau Boss)
            if (isValidCombatTarget(victim)) {
                removeCombat(attacker);
                attacker.sendSystemMessage(Component.literal("§a[✔] Your target died! You left combat instantly."));
            }
        }
    }

    @SubscribeEvent
    public static void onCommandUse(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            if (isInCombat(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§4[!] You cannot use commands while in combat!"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (player.tickCount % 10 == 0) {
                if (combatMap.containsKey(player.getUUID())) {
                    long remaining = combatMap.get(player.getUUID()) - System.currentTimeMillis();
                    if (remaining <= 0) {
                        removeCombat(player);
                        player.sendSystemMessage(Component.literal("§a[✔] You left combat! You are safe now."));
                    } else {
                        PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncCombat(true, remaining), player);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isInCombat(player)) {
                player.kill();
                removeCombat(player);

                MinecraftServer server = player.getServer();
                if (server != null) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("§c[☠] §4" + player.getName().getString() + " §cleft the server during combat and died!"),
                            false
                    );
                }
            }
        }
    }
}
