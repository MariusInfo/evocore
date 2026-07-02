package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent; // AICI E SECRETUL NOU!
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "evocore")
public class GodCommand {

    // Lista cu adminii care au god mode activat
    private static final Set<UUID> godPlayers = new HashSet<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("god")
                .requires(source -> hasLuckPermission(source, "evocore.god")) // <-- LUCKPERMS APLICAT AICI
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID uuid = player.getUUID();

                    if (godPlayers.contains(uuid)) {
                        godPlayers.remove(uuid);
                        player.sendSystemMessage(Component.literal("§c✖ Modul GOD a fost DEZACTIVAT. Acum poți lua damage."));
                    } else {
                        godPlayers.add(uuid);
                        player.sendSystemMessage(Component.literal("§a✔ Modul GOD a fost ACTIVAT. Ești invincibil!"));

                        // Îi stingem focul și îi dăm viața full la activare!
                        player.clearFire();
                        player.setHealth(player.getMaxHealth());
                    }

                    return 1;
                })
        );
    }

    // ==========================================
    // VERIFICARE LUCKPERMS
    // ==========================================
    private static boolean hasLuckPermission(CommandSourceStack source, String permission) {
        try {
            if (source.hasPermission(2)) return true; // OP are mereu acces
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            return source.hasPermission(2); // Dacă dă eroare (ex: execută consola), verifică OP
        }
    }

    // BLOCĂM ATACUL COMPLET (Nu doar damage-ul, ci și knockback-ul/armura stricată)
    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (godPlayers.contains(player.getUUID())) {
                event.setCanceled(true); // Anulează lovitura de tot!
            }
        }
    }

    // Scoatem jucătorul din God Mode dacă iese de pe server (să nu rămână blocat)
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        godPlayers.remove(event.getEntity().getUUID());
    }
}