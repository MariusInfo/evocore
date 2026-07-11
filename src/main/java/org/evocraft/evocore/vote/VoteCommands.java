package org.evocraft.evocore.vote;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.network.PacketHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoteCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMANDA: /vote (Pentru jucători)
        // ==========================================
        dispatcher.register(Commands.literal("vote")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    Component link = Component.literal("§a§l[CLICK HERE TO VOTE]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://evocraft.ro/vote.php"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eOpen the vote link!"))));

                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));
                    player.sendSystemMessage(Component.literal("§e⭐ §lVOTE FOR THE SERVER §e⭐"));
                    player.sendSystemMessage(link);
                    player.sendSystemMessage(Component.literal("§7(Reward: §aVote Key§7)"));
                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));

                    return 1;
                })
        );

        // ==========================================
        // COMANDA ASCUNSĂ: /adminvote <nume> (Executată de Votifier din consolă)
        // ==========================================
        dispatcher.register(Commands.literal("adminvote")
                .requires(source -> source.hasPermission(2)) // Doar consola/operatorii pot rula asta
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");

                            // Căutăm UUID-ul jucătorului după nume
                            context.getSource().getServer().getProfileCache().get(playerName).ifPresentOrElse(profile -> {
                                UUID uuid = profile.getId();

                                // =================================================================
                                // 1. ADĂUGARE CHEIE ÎN BAZA DE DATE (ASINCRON PENTRU A EVITA LAG-UL)
                                // =================================================================
                                CompletableFuture.runAsync(() -> CrateKeyManager.get().addKeys(playerName, "vote", 1));

                                // 2. Mesajul care va apărea peste tot
                                String guiMessage = "§e" + playerName + " §fvoted and received a §aVote Key§f!";
                                Component chatMessage = Component.literal("§8[§a§lVOTE§8] " + guiMessage + " Type §e/vote §ffor the link.");

                                // 3. Trimitem pe CHAT și pe ECRAN (GUI) la toată lumea!
                                for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                    // Mesaj în chat
                                    player.sendSystemMessage(chatMessage);

                                    // Notificarea GUI care glisează de sus!
                                    PacketHandler.sendToPlayer(new PacketHandler.S2C_BroadcastNotification("§a§lVOTE: §f" + guiMessage), player);
                                }

                                // 4. Trimitem un mesaj extra jucătorului dacă e online
                                ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                if (onlinePlayer != null) {
                                    onlinePlayer.sendSystemMessage(Component.literal("§a✔ Thank you for voting! The key was added to your account."));
                                }

                            }, () -> {
                                context.getSource().sendFailure(Component.literal("Player " + playerName + " does not exist in the database."));
                            });

                            return 1;
                        })
                )
        );
    }
}
