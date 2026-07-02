package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.data.EconomyManager;

public class EconomyCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // --- COMANDA /BANI ---
        dispatcher.register(Commands.literal("bani")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    double bal = EconomyManager.get().getBalance(p.getUUID());
                    p.sendSystemMessage(Component.literal("§a[Banca] §fBalanța ta: §e" + String.format("%,.0f", bal) + " Lei"));
                    return 1;
                })
                // /bani give <jucator> <suma>
                .then(Commands.literal("give")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            EconomyManager.get().addBalance(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a[!] I-ai dat §e" + amount + " Lei§a lui " + target.getGameProfile().getName()), true);
                                            target.sendSystemMessage(Component.literal("§a[Banca] §fAi primit §e" + amount + " Lei§f de la Admin."));
                                            return 1;
                                        }))))
                // /bani set <jucator> <suma>
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            EconomyManager.get().setBalance(target.getUUID(), amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§a[!] I-ai setat balanța lui " + target.getGameProfile().getName() + " la §e" + amount + " Lei"), true);
                                            return 1;
                                        }))))
        );

        // --- COMANDA /PAY ---
        dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
                                .executes(ctx -> {
                                    ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    double amount = DoubleArgumentType.getDouble(ctx, "amount");

                                    if (sender.getUUID().equals(target.getUUID())) {
                                        sender.sendSystemMessage(Component.literal("§c[!] Nu îți poți trimite bani singur!"));
                                        return 0;
                                    }

                                    if (EconomyManager.get().getBalance(sender.getUUID()) >= amount) {
                                        EconomyManager.get().removeBalance(sender.getUUID(), amount);
                                        EconomyManager.get().addBalance(target.getUUID(), amount);
                                        sender.sendSystemMessage(Component.literal("§a[Banca] §fAi trimis §e" + amount + " Lei§f lui " + target.getGameProfile().getName()));
                                        target.sendSystemMessage(Component.literal("§a[Banca] §fAi primit §e" + amount + " Lei§f de la " + sender.getGameProfile().getName()));
                                    } else {
                                        sender.sendSystemMessage(Component.literal("§c[Banca] Fonduri insuficiente!"));
                                    }
                                    return 1;
                                })))
        );
    }
}