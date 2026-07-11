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
                                Component.literal("§8[§c§lNOTICE§8] §fGlobal chat was §cDISABLED §fby an administrator!"), false);
                    } else {
                        context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                                Component.literal("§8[§c§lNOTICE§8] §fGlobal chat was §aENABLED§f! You can talk again."), false);
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
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("minute", IntegerArgumentType.integer(1)) // Minim 1 minut

                                // Execuție FĂRĂ Motiv
                                .executes(context -> applyMute(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "minute"), "Unspecified"))

                                // Execuție CU Motiv
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> applyMute(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "minute"), StringArgumentType.getString(context, "reason")))
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
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");

                            ChatManager.activeMutes.remove(target.getUUID());
                            context.getSource().sendSuccess(() -> Component.literal("§aRemoved mute from " + target.getName().getString()), false);
                            target.sendSystemMessage(Component.literal("§a✔ You were unmuted by an administrator! You can talk in chat."));
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
        target.sendSystemMessage(Component.literal("§c§lYOU HAVE BEEN MUTED!"));
        target.sendSystemMessage(Component.literal("§7Duration: §c" + minutes + " minutes §7(timer decreases only while online)"));
        target.sendSystemMessage(Component.literal("§7Reason: §f" + reason));
        target.sendSystemMessage(Component.literal("§8§m--------------------------------"));

        // Anunțăm staff-ul / publicul
        source.sendSuccess(() -> Component.literal("§aMuted " + target.getName().getString() + " for " + minutes + " minutes."), true);
        return 1;
    }
}
