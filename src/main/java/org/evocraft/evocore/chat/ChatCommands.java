package org.evocraft.evocore.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ChatCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMANDA: /mutechat (Oprește/Pornește tot chat-ul)
        // ==========================================
        dispatcher.register(Commands.literal("mutechat")
                .requires(source -> {
                    try { return ChatManager.hasPermission(source.getPlayerOrException(), "evocore.mutechat"); }
                    catch (Exception e) { return source.hasPermission(2); }
                })
                .executes(context -> {
                    ChatManager.isGlobalChatMuted = !ChatManager.isGlobalChatMuted;

                    if (ChatManager.isGlobalChatMuted) {
                        context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                Component.literal("§8[§c§lANUNȚ§8] §fChat-ul global a fost §cOPRIT §fde către un administrator!"), false);
                    } else {
                        context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                Component.literal("§8[§c§lANUNȚ§8] §fChat-ul global a fost §aPORNIT§f! Acum puteți vorbi din nou."), false);
                    }
                    return 1;
                })
        );

        // ==========================================
        // COMANDA: /mute <Jucator> <Minute> [Motiv]
        // ==========================================
        dispatcher.register(Commands.literal("mute")
                .requires(source -> {
                    try { return ChatManager.hasPermission(source.getPlayerOrException(), "evocore.mute"); }
                    catch (Exception e) { return source.hasPermission(2); }
                })
                .then(Commands.argument("jucator", EntityArgument.player())
                        .then(Commands.argument("minute", IntegerArgumentType.integer(1)) // Minim 1 minut

                                // Execuție FĂRĂ Motiv
                                .executes(context -> applyMute(context.getSource(), EntityArgument.getPlayer(context, "jucator"), IntegerArgumentType.getInteger(context, "minute"), "Nespecificat"))

                                // Execuție CU Motiv
                                .then(Commands.argument("motiv", StringArgumentType.greedyString())
                                        .executes(context -> applyMute(context.getSource(), EntityArgument.getPlayer(context, "jucator"), IntegerArgumentType.getInteger(context, "minute"), StringArgumentType.getString(context, "motiv")))
                                )
                        ))
        );

        // ==========================================
        // COMANDA: /unmute <Jucator>
        // ==========================================
        dispatcher.register(Commands.literal("unmute")
                .requires(source -> {
                    try { return ChatManager.hasPermission(source.getPlayerOrException(), "evocore.mute"); }
                    catch (Exception e) { return source.hasPermission(2); }
                })
                .then(Commands.argument("jucator", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "jucator");

                            ChatManager.activeMutes.remove(target.getUUID());
                            context.getSource().sendSuccess(() -> Component.literal("§aI-ai scos mute-ul lui " + target.getName().getString()), false);
                            target.sendSystemMessage(Component.literal("§a✔ Ai primit UNMUTE de la un administrator! Poți vorbi pe chat."));
                            return 1;
                        })
                )
        );
    }

    private static int applyMute(CommandSourceStack source, ServerPlayer target, int minutes, String reason) {
        int seconds = minutes * 60;
        ChatManager.activeMutes.put(target.getUUID(), seconds);

        // Anunțăm jucătorul
        target.sendSystemMessage(Component.literal("§8§m--------------------------------"));
        target.sendSystemMessage(Component.literal("§c§lAI PRIMIT MUTE!"));
        target.sendSystemMessage(Component.literal("§7Durată: §c" + minutes + " Minute §7(Timpul scade doar online)"));
        target.sendSystemMessage(Component.literal("§7Motiv: §f" + reason));
        target.sendSystemMessage(Component.literal("§8§m--------------------------------"));

        // Anunțăm staff-ul / publicul
        source.sendSuccess(() -> Component.literal("§aI-ai dat mute lui " + target.getName().getString() + " pentru " + minutes + " minute."), true);
        return 1;
    }
}