package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.combat.CombatLogManager;
import org.evocraft.evocore.data.FlyTimeManager;
import org.evocraft.evocore.network.PacketHandler; // Importăm rețeaua

public class FlyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fly")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    // ========================================================
                    // 0. Detectăm pe ce server suntem pe baza portului!
                    // Serverul Creative are portul 5002
                    // ========================================================
                    int port = player.getServer().getPort();
                    boolean isCreative = (port == 5002);

                    // ========================================================
                    // 1. Verificăm cât timp are voie să zboare
                    // ========================================================
                    int maxSeconds;

                    if (isCreative) {
                        // Pe Creative, absolut toată lumea are fly NELIMITAT
                        maxSeconds = -1;
                    } else {
                        // Pe Survival/Altele, citește permisiunile normale: fly.limit.1, fly.unlimited, etc.
                        maxSeconds = FlyTimeManager.getMaxFlySeconds(player);
                    }

                    if (maxSeconds == 0) {
                        player.sendSystemMessage(Component.literal("§c✖ Nu ai rank-ul necesar pentru a folosi fly!"));
                        return 0;
                    }

                    // 2. Verificăm dacă și-a consumat deja timpul pe ziua respectivă
                    if (maxSeconds != -1 && FlyTimeManager.getUsedSeconds(player) >= maxSeconds) {
                        player.sendSystemMessage(Component.literal("§c✖ Ți-ai consumat limita de zbor pe ziua de azi!"));
                        return 0;
                    }

                    // 3. Verificăm combat log-ul tău
                    if (CombatLogManager.isInCombat(player)) {
                        player.sendSystemMessage(Component.literal("§c✖ Nu poți activa fly-ul cât ești în combat!"));
                        return 0;
                    }

                    // 4. Logica de Activare / Dezactivare
                    boolean isFlying = player.getAbilities().mayfly;

                    if (isFlying) {
                        // OPREȘTE ZBORUL
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                        player.sendSystemMessage(Component.literal("§eModul de zbor a fost §cDEZACTIVAT§e!"));

                        // Trimitem pachetul să închidă interfața custom de pe ecran instantaneu!
                        PacketHandler.sendToPlayer(
                                new PacketHandler.S2C_SyncFlyTime(false, 0), player
                        );
                    } else {
                        // PORNEȘTE ZBORUL
                        player.getAbilities().mayfly = true;
                        player.sendSystemMessage(Component.literal("§eModul de zbor a fost §aACTIVAT§e!"));

                        // Îi dăm un mesaj frumos cu timpul rămas la activare și facem update la interfață
                        if (maxSeconds == -1) {
                            player.sendSystemMessage(Component.literal("§7[!] Ai timp de zbor §aNelimitat§7."));

                            // Trimitem pachetul pe client ca interfața să știe că e nelimitat
                            PacketHandler.sendToPlayer(
                                    new PacketHandler.S2C_SyncFlyTime(true, -1), player
                            );
                        } else {
                            int secondsLeft = maxSeconds - FlyTimeManager.getUsedSeconds(player);
                            int m = secondsLeft / 60;
                            player.sendSystemMessage(Component.literal("§7[!] Timp rămas azi: §f" + m + " min §7(se consumă doar când ești în aer)."));

                            // Trimitem pachetul pe client ca interfața să arate secundele corecte
                            PacketHandler.sendToPlayer(
                                    new PacketHandler.S2C_SyncFlyTime(true, secondsLeft), player
                            );
                        }
                    }

                    player.onUpdateAbilities();
                    return 1;
                })
        );
    }
}