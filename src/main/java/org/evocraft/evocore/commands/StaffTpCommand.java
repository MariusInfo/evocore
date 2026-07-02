package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StaffTpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stafftp")
                .requires(source -> source.hasPermission(2)) // Restricție de staff (Nivel OP 2)
                .then(Commands.argument("jucator", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(context, "jucator");

                            if (player.getUUID().equals(target.getUUID())) {
                                player.sendSystemMessage(Component.literal("§c✖ Nu te poți teleporta la tine însuți!"));
                                return 0;
                            }

                            // Îl teleportăm la nivelul (dimensiunea) și poziția exactă a jucătorului țintă
                            player.teleportTo(
                                    target.serverLevel(),
                                    target.getX(),
                                    target.getY(),
                                    target.getZ(),
                                    target.getYRot(),
                                    target.getXRot()
                            );

                            player.sendSystemMessage(Component.literal("§8[§cStaff§8] §fTe-ai teleportat la jucătorul §e" + target.getGameProfile().getName() + "§f."));

                            return 1;
                        })
                )
        );
    }
}