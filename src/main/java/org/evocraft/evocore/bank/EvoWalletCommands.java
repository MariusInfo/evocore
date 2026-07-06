package org.evocraft.evocore.bank;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class EvoWalletCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wallet")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, p) -> new EvoWalletMenu(containerId, inventory),
                            Component.literal("\u00A7a\u00A7lEvoWallet")
                    ));
                    return 1;
                }));
    }
}
