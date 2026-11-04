package org.evocraft.evokits.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evokits.data.KitManager;

import java.util.ArrayList;
import java.util.List;

public class KitCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMANDA: /kit [nume]
        // ==========================================
        dispatcher.register(Commands.literal("kit")
                .executes(context -> {
                    listKits(context.getSource());
                    return 1;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(KitManager.get().kits.keySet(), builder))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String kitName = StringArgumentType.getString(context, "name");
                            KitManager.get().claimKit(player, kitName);
                            return 1;
                        }))
        );

        dispatcher.register(Commands.literal("kits")
                .executes(context -> {
                    listKits(context.getSource());
                    return 1;
                })
        );

        // ==========================================
        // COMANDA ADMIN: /evokit
        // ==========================================
        dispatcher.register(Commands.literal("evokit")
                .requires(source -> source.hasPermission(2))

                // Creează kit din inventar
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("cooldownHours", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(context, "name");
                                            int cd = IntegerArgumentType.getInteger(context, "cooldownHours");

                                            List<ItemStack> items = new ArrayList<>();
                                            for (int i = 0; i < 36; i++) {
                                                ItemStack stack = player.getInventory().getItem(i);
                                                if (!stack.isEmpty()) {
                                                    items.add(stack.copy());
                                                }
                                            }

                                            if (items.isEmpty()) {
                                                player.sendSystemMessage(Component.literal("§cYour inventory is empty! Put items in your inventory to create the kit."));
                                                return 0;
                                            }

                                            KitManager.get().createKit(name, cd, items);
                                            player.sendSystemMessage(Component.literal("§a✔ Kit §l" + name + " §awas created and saved! Cooldown: §e" + cd + "h"));
                                            return 1;
                                        }))))

                // Șterge un kit
                .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(KitManager.get().kits.keySet(), builder))
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    KitManager.get().deleteKit(name);
                                    context.getSource().sendSuccess(() -> Component.literal("§c✔ Kit " + name + " was deleted!"), true);
                                    return 1;
                                })))

                // Leagă o cheie de crate de kit
                .then(Commands.literal("addkey")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("kit", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(KitManager.get().kits.keySet(), builder))
                                        .executes(context -> {
                                            String key = StringArgumentType.getString(context, "key");
                                            String kit = StringArgumentType.getString(context, "kit");
                                            boolean added = KitManager.get().addCrateKey(key, kit);
                                            if (!added) {
                                                context.getSource().sendFailure(Component.literal("§c[EvoKits] Kit not found or key name is invalid."));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("§a[EvoKits] Kit §e" + kit + " §awill now give one §e" + key.toLowerCase() + " §acrate key."), true);
                                            return 1;
                                        }))))
        );
    }

    private static void listKits(CommandSourceStack source) {
        if (KitManager.get().kits.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNo kits are currently configured on the server."), false);
            return;
        }

        source.sendSuccess(() -> Component.literal("§8§m--------------------------------"), false);
        source.sendSuccess(() -> Component.literal("§6§lAVAILABLE KITS:"), false);

        ServerPlayer player = null;
        try { player = source.getPlayerOrException(); } catch (Exception ignored) {}

        for (KitManager.Kit kit : KitManager.get().kits.values()) {
            boolean hasPerm = player == null || KitManager.hasPermission(player, kit.permission) || player.hasPermissions(2);
            long cdSec = player != null ? KitManager.get().getRemainingCooldownSeconds(player.getUUID(), kit.name) : 0;

            String status;
            if (!hasPerm) {
                status = "§c[No Permission]";
            } else if (cdSec > 0 && (player == null || !player.hasPermissions(2))) {
                long h = cdSec / 3600;
                long m = (cdSec % 3600) / 60;
                long s = cdSec % 60;
                status = "§e[Cooldown: " + h + "h " + m + "m " + s + "s]";
            } else {
                status = "§a[Ready to Claim - /kit " + kit.name + "]";
            }

            final String line = "§7- §f" + kit.name + " " + status;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("§8§m--------------------------------"), false);
    }
}
