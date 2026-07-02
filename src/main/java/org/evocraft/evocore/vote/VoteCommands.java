package org.evocraft.evocore.vote;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.network.PacketHandler;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

                    Component link = Component.literal("§a§l[CLICK AICI PENTRU A VOTA]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://evocraft.ro/vote.php"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eDeschide link-ul de vot!"))));

                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));
                    player.sendSystemMessage(Component.literal("§e⭐ §lVOTEAZĂ SERVERUL §e⭐"));
                    player.sendSystemMessage(link);
                    player.sendSystemMessage(Component.literal("§7(Recompense: §aCheie Vote§7)"));
                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));

                    return 1;
                })
        );

        // ==========================================
        // COMANDA ASCUNSĂ: /adminvote <nume> (Executată de Votifier din consolă)
        // ==========================================
        dispatcher.register(Commands.literal("adminvote")
                .requires(source -> source.hasPermission(2)) // Doar consola/operatorii pot rula asta
                .then(Commands.argument("jucator", StringArgumentType.string())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "jucator");

                            // Căutăm UUID-ul jucătorului după nume
                            context.getSource().getServer().getProfileCache().get(playerName).ifPresentOrElse(profile -> {
                                UUID uuid = profile.getId();

                                // =================================================================
                                // 1. ADĂUGARE CHEIE ÎN BAZA DE DATE (ASINCRON PENTRU A EVITA LAG-UL)
                                // =================================================================
                                CompletableFuture.runAsync(() -> {
                                    String query = "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, 'vote', 1) ON DUPLICATE KEY UPDATE amount = amount + 1";

                                    // FIX: Luăm conexiunea FĂRĂ să o punem în try() pentru a nu se închide automat la final!
                                    Connection conn = DatabaseManager.get().getConnection();

                                    // PreparedStatement-ul rămâne în try() pentru că el trebuie închis (curățat) din memorie
                                    try (PreparedStatement ps = conn.prepareStatement(query)) {

                                        ps.setString(1, playerName);
                                        ps.executeUpdate();

                                    } catch (Exception e) {
                                        System.err.println("[EvoVotifier] Eroare la adaugarea cheii in baza de date pentru: " + playerName);
                                        e.printStackTrace();
                                    }
                                });

                                // 2. Mesajul care va apărea peste tot
                                String guiMessage = "§e" + playerName + " §fa votat și a primit §acheie vote§f!";
                                Component chatMessage = Component.literal("§8[§a§lVOT§8] " + guiMessage + " Scrie §e/vote §fpentru link.");

                                // 3. Trimitem pe CHAT și pe ECRAN (GUI) la toată lumea!
                                for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                    // Mesaj în chat
                                    player.sendSystemMessage(chatMessage);

                                    // Notificarea GUI care glisează de sus!
                                    PacketHandler.sendToPlayer(new PacketHandler.S2C_BroadcastNotification("§a§lVOT: §f" + guiMessage), player);
                                }

                                // 4. Trimitem un mesaj extra jucătorului dacă e online
                                ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                if (onlinePlayer != null) {
                                    onlinePlayer.sendSystemMessage(Component.literal("§a✔ Îți mulțumim pentru vot! Cheia a fost adaugata in contul tău."));
                                }

                            }, () -> {
                                context.getSource().sendFailure(Component.literal("Jucătorul " + playerName + " nu există în baza de date."));
                            });

                            return 1;
                        })
                )
        );
    }
}