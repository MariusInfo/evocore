package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.data.EconomyManager;
import org.evocraft.evocore.util.EvoCurrencyFormatter;

public class EconomyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // --- MONEY COMMAND ---
        dispatcher.register(Commands.literal("bani")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    double bal = EconomyManager.get().getBalance(p.getUUID());
                    p.sendSystemMessage(Component.literal("§a[EvoBank] §fYour balance: §e" + EvoCurrencyFormatter.formatWithCurrency(bal)));
                    return 1;
                })
                // /bani give <target> <amount>
                .then(Commands.literal("give")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            EconomyManager.get().addBalance(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a[!] Added §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§a to " + target.getGameProfile().getName()), true);
                                            target.sendSystemMessage(Component.literal("§a[EvoBank] §fYou received §e" + EvoCurrencyFormatter.formatWithCurrency(amount) + "§f from an admin."));
                                            return 1;
                                        }))))
                // /bani set <target> <amount>
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            EconomyManager.get().setBalance(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a[!] Set " + target.getGameProfile().getName() + "'s balance to §e" + EvoCurrencyFormatter.formatWithCurrency(amount)), true);
                                            return 1;
                                        }))))
        );

        // --- /PAY IS DISABLED ---
        dispatcher.register(Commands.literal("pay")
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("§c[EvoBank] /pay is disabled. Use an EvoBank ATM transfer instead."));
                    return 0;
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.literal("§c[EvoBank] /pay is disabled. Use an EvoBank ATM transfer instead."));
                                    return 0;
                                })))
        );
    }
}
