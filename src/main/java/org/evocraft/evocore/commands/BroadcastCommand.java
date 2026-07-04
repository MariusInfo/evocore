package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.network.PacketHandler;

public class BroadcastCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("bc")
                .requires(source -> hasLuckPermission(source, "evocore.announce"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message").replace("&", "§");
                            Component chatMessage = Component.literal("§8[§c§lANNOUNCEMENT§8] §f" + message);
                            context.getSource().getServer().getPlayerList().broadcastSystemMessage(chatMessage, false);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("tbc")
                .requires(source -> hasLuckPermission(source, "evocore.announce"))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message").replace("&", "§");
                            Component chatMessage = Component.literal("§8[§c§lIMPORTANT§8] §e" + message);

                            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                player.sendSystemMessage(chatMessage);
                                PacketHandler.sendToPlayer(new PacketHandler.S2C_BroadcastNotification(message), player);
                            }

                            return 1;
                        })));
    }

    public static boolean hasLuckPermission(CommandSourceStack source, String permission) {
        try {
            if (source.hasPermission(2)) return true;
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            return source.hasPermission(2);
        }
    }
}
