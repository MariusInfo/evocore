package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.evocraft.evocore.data.EconomyManager;
import org.evocraft.evocore.data.KitManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AdminCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMENZI ECONOMIE (/eco give/take/set/item)
        // ==========================================
        dispatcher.register(Commands.literal("eco")
                .requires(source -> source.hasPermission(2)) // Doar Operatorii

                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().addBalance(player.getUUID(), amount);
                                                player.sendSystemMessage(Component.literal("§aAi primit §e" + amount + " Lei§a!"));
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aAi dat §e" + amount + " Lei §acelor " + players.size() + " jucători."), true);
                                            return 1;
                                        }))))

                .then(Commands.literal("take")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().removeBalance(player.getUUID(), amount);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aAi retras §e" + amount + " Lei."), true);
                                            return 1;
                                        }))))

                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .executes(context -> {
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            for (ServerPlayer player : players) {
                                                EconomyManager.get().setBalance(player.getUUID(), amount);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§aBalanța a fost setată la §e" + amount + " Lei."), true);
                                            return 1;
                                        }))))

                // ==========================================
                // NOU: CREEAZĂ ITEM FIZIC CU BANI PENTRU KIT-URI
                // ==========================================
                .then(Commands.literal("item")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                    ItemStack moneyItem = new ItemStack(Items.PAPER);
                                    moneyItem.setHoverName(Component.literal("§e§lBANI: §a" + amount + " Lei"));
                                    moneyItem.getOrCreateTag().putInt("EvoMoney", amount);

                                    player.getInventory().add(moneyItem);
                                    player.sendSystemMessage(Component.literal("§a✔ Ai primit item-ul de §e" + amount + " Lei§a. Pune-l în inventar și dă /evokit create!"));
                                    return 1;
                                })))
        );

        // ==========================================
        // COMENZI KIT-URI (/evokit create/delete)
        // ==========================================
        dispatcher.register(Commands.literal("evokit")
                .requires(source -> source.hasPermission(2)) // Doar Operatorii

                // Creează un kit din inventarul tău!
                // Ex: /evokit create VIP 24 (Kit numit VIP, cooldown 24 ore)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("cooldownHours", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(context, "name");
                                            int cd = IntegerArgumentType.getInteger(context, "cooldownHours");

                                            List<ItemStack> items = new ArrayList<>();
                                            // Citim doar primele 36 sloturi (Inventarul jucătorului + Hotbar-ul)
                                            for (int i = 0; i < 36; i++) {
                                                ItemStack stack = player.getInventory().getItem(i);
                                                if (!stack.isEmpty()) {
                                                    items.add(stack.copy());
                                                }
                                            }

                                            if (items.isEmpty()) {
                                                player.sendSystemMessage(Component.literal("§cInventarul tău este gol! Pune iteme în inventar pentru a crea kit-ul."));
                                                return 0;
                                            }

                                            KitManager.get().createKit(name, cd, items);
                                            player.sendSystemMessage(Component.literal("§a✔ Kit-ul §l" + name + " §aa fost creat și salvat!"));
                                            return 1;
                                        }))))

                // Șterge un kit
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    KitManager.get().deleteKit(name);
                                    context.getSource().sendSuccess(() -> Component.literal("§c✔ Kit-ul " + name + " a fost șters din baza de date!"), true);
                                    return 1;
                                })))

                .then(Commands.literal("addkey")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("kit", StringArgumentType.word())
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");
                                            String kit = StringArgumentType.getString(context, "kit");
                                            boolean added = KitManager.get().addCrateKey(key, kit);
                                            if (!added) {
                                                context.getSource().sendFailure(Component.literal("§c[EvoCore] Kit not found or key name is invalid."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Kit §e" + kit + " §awill now give one §e" + key.toLowerCase() + " §acrate key."), true);
                                            return 1;
                                        })))
                )
        );
        // ==========================================
        // COMENZI WARP-URI (/evowarp create/delete)
        // ==========================================
        dispatcher.register(Commands.literal("evowarp")
                .requires(source -> source.hasPermission(2)) // Doar Staff

                .then(Commands.literal("create")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                                    ItemStack icon = player.getMainHandItem();

                                    if (icon.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("§cTrebuie să ții un item în mână pentru a-l seta ca iconiță a warp-ului!"));
                                        return 0;
                                    }

                                    org.evocraft.evocore.data.WarpManager.get().createWarp(name, player, icon);
                                    player.sendSystemMessage(Component.literal("§a✔ Warp-ul §l" + name + " §aa fost creat la locația ta exactă!"));
                                    return 1;
                                })))

                .then(Commands.literal("delete")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(context -> {
                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
                                    org.evocraft.evocore.data.WarpManager.get().deleteWarp(name);
                                    context.getSource().sendSuccess(() -> Component.literal("§c✔ Warp-ul " + name + " a fost șters!"), true);
                                    return 1;
                                })))
        );
    }
}
