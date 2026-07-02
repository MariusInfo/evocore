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

        // COMANDA 1: /bc (Doar Chat)
        dispatcher.register(Commands.literal("bc")
                .requires(source -> hasLuckPermission(source, "evocore.announce"))
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "mesaj").replace("&", "§");
                            Component chatMessage = Component.literal("§8[§c§lANUNȚ§8] §f" + message);
                            context.getSource().getServer().getPlayerList().broadcastSystemMessage(chatMessage, false);
                            return 1;
                        })));

        // COMANDA 2: /tbc (Notificare GUI pe ecran + Chat)
        dispatcher.register(Commands.literal("tbc")
                .requires(source -> hasLuckPermission(source, "evocore.announce"))
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "mesaj").replace("&", "§");
                            Component chatMessage = Component.literal("§8[§c§lANUNȚ IMPORTANT§8] §e" + message);

                            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                // Trimitem în Chat
                                player.sendSystemMessage(chatMessage);

                                // Trimitem NOTIFICAREA GRAFICĂ SUS PE ECRAN!
                                PacketHandler.sendToPlayer(new PacketHandler.S2C_BroadcastNotification(message), player);
                            }

                            return 1;
                        })));
    }

    // ==========================================
    // VERIFICARE LUCKPERMS
    // ==========================================
    public static boolean hasLuckPermission(CommandSourceStack source, String permission) {
        try {
            if (source.hasPermission(2)) return true; // OP are mereu acces
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            return source.hasPermission(2); // Dacă dă eroare (ex: execută consola), verifică OP
        }
    }
}