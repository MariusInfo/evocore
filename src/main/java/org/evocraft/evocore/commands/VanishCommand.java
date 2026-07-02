package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.vanish.VanishManager;

public class VanishCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Comanda principală: /vanish
        dispatcher.register(Commands.literal("vanish")
                .requires(source -> {
                    try {
                        if (source.hasPermission(2)) return true; // OP-ul trece automat
                        net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                        net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
                        return user != null && user.getCachedData().getPermissionData().checkPermission("evocore.vanish").asBoolean();
                    } catch (Exception e) {
                        return source.hasPermission(2);
                    }
                }) // Folosind LuckPerms
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return VanishManager.toggleVanish(player);
                })
        );

        // Scurtătură: /v
        dispatcher.register(Commands.literal("v")
                .requires(source -> {
                    try {
                        if (source.hasPermission(2)) return true; // OP-ul trece automat
                        net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                        net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
                        return user != null && user.getCachedData().getPermissionData().checkPermission("evocore.vanish").asBoolean();
                    } catch (Exception e) {
                        return source.hasPermission(2);
                    }
                })
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return VanishManager.toggleVanish(player);
                })
        );
    }
}