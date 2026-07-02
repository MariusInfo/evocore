package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class InvseeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("invsee")
                .requires(source -> {
                    try {
                        if (source.hasPermission(2)) return true; // OP-ul trece automat
                        net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
                        net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
                        return user != null && user.getCachedData().getPermissionData().checkPermission("evocore.invsee").asBoolean();
                    } catch (Exception e) {
                        return source.hasPermission(2);
                    }
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer sourcePlayer = context.getSource().getPlayerOrException();
                            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "target");

                            if (sourcePlayer.getUUID().equals(targetPlayer.getUUID())) {
                                sourcePlayer.sendSystemMessage(Component.literal("§c✖ Îți poți deschide propriul inventar cu tasta E!"));
                                return 0;
                            }

                            // ==========================================
                            // WRAPPER MAGIC: Păcălește Minecraft-ul să nu închidă meniul
                            // ==========================================
                            Container invWrapper = new Container() {
                                @Override public int getContainerSize() { return 36; }
                                @Override public boolean isEmpty() { return targetPlayer.getInventory().isEmpty(); }
                                @Override public ItemStack getItem(int slot) { return targetPlayer.getInventory().getItem(slot); }
                                @Override public ItemStack removeItem(int slot, int count) { return targetPlayer.getInventory().removeItem(slot, count); }
                                @Override public ItemStack removeItemNoUpdate(int slot) { return targetPlayer.getInventory().removeItemNoUpdate(slot); }
                                @Override public void setItem(int slot, ItemStack stack) { targetPlayer.getInventory().setItem(slot, stack); }
                                @Override public void setChanged() { targetPlayer.getInventory().setChanged(); }
                                @Override public void clearContent() { targetPlayer.getInventory().clearContent(); }

                                // AICI E SECRETUL: Serverul nu va mai închide meniul din cauza distanței!
                                @Override public boolean stillValid(Player player) { return true; }
                            };

                            sourcePlayer.openMenu(new SimpleMenuProvider(
                                    (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, invWrapper, 4),
                                    Component.literal("Inventar: " + targetPlayer.getName().getString())
                            ));

                            sourcePlayer.sendSystemMessage(Component.literal("§a✔ Inspectezi inventarul lui §l" + targetPlayer.getName().getString()));
                            return 1;
                        })
                )
        );
    }
}