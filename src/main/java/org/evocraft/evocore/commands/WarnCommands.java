package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.data.WarnManager;

import java.util.List;

public class WarnCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMANDA: /warn <Jucator> <Motiv>
        // ==========================================
        dispatcher.register(Commands.literal("warn")
                .requires(source -> hasLuckPermission(source, "evocore.warn"))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    String reason = StringArgumentType.getString(context, "reason");
                                    String admin = context.getSource().getTextName();

                                    WarnManager.addWarn(target.getUUID(), reason, admin);

                                    target.sendSystemMessage(Component.literal("§8§m--------------------------------"));
                                    target.sendSystemMessage(Component.literal("§c§lYOU HAVE RECEIVED A WARNING!"));
                                    target.sendSystemMessage(Component.literal("§7Reason: §f" + reason));
                                    target.sendSystemMessage(Component.literal("§7Admin: §e" + admin));
                                    target.sendSystemMessage(Component.literal("§8§m--------------------------------"));

                                    context.getSource().sendSuccess(() -> Component.literal("§aWarned " + target.getName().getString()), true);
                                    return 1;
                                })
                        ))
        );

        // ==========================================
        // COMANDA: /warns <Jucator> (Vezi toate warn-urile)
        // ==========================================
        dispatcher.register(Commands.literal("warns")
                .requires(source -> hasLuckPermission(source, "evocore.warn.view"))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");

                            // Executăm asincron pentru a nu bloca serverul la citirea din DB
                            new Thread(() -> {
                                List<WarnManager.WarnEntry> warns = WarnManager.getWarns(target.getUUID());
                                context.getSource().sendSuccess(() -> Component.literal("§e§lWarnings for " + target.getName().getString() + " (§c" + warns.size() + "§e):"), false);

                                for (WarnManager.WarnEntry w : warns) {
                                    context.getSource().sendSuccess(() -> Component.literal("§6#" + w.id() + " §8| §f" + w.reason() + " §8| §7Admin: §e" + w.admin() + " §8| §b" + w.date().substring(0, 10)), false);
                                }
                            }).start();

                            return 1;
                        })
                )
        );

        // ==========================================
        // COMANDA: /unwarn <ID_Warn>
        // ==========================================
        dispatcher.register(Commands.literal("unwarn")
                .requires(source -> hasLuckPermission(source, "evocore.warn.remove"))
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int id = IntegerArgumentType.getInteger(context, "id");
                            WarnManager.removeWarn(id, () -> {
                                context.getSource().sendSuccess(() -> Component.literal("§a✔ Warning #" + id + " was removed successfully!"), true);
                            });
                            return 1;
                        })
                )
        );
    }

    private static boolean hasLuckPermission(CommandSourceStack source, String permission) {
        try {
            if (source.hasPermission(2)) return true;
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) { return source.hasPermission(2); }
    }
}
