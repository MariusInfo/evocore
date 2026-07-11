package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class StaffGmCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("staffgm")
                .requires(source -> source.hasPermission(2)) // Restricție de staff (Nivel OP 2)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();

                    // Dacă jucătorul este în Spectator, îl dăm în Survival. Altfel, îl dăm în Spectator.
                    if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        player.setGameMode(GameType.SURVIVAL);
                        player.sendSystemMessage(Component.literal("§8[§cStaff§8] §fYour game mode was changed to §aSURVIVAL§f."));
                    } else {
                        player.setGameMode(GameType.SPECTATOR);
                        player.sendSystemMessage(Component.literal("§8[§cStaff§8] §fYour game mode was changed to §7SPECTATOR§f."));
                    }

                    return 1;
                })
        );
    }
}
