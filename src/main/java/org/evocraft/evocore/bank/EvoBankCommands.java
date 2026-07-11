package org.evocraft.evocore.bank;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Comparator;
import java.util.List;

public class EvoBankCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evobank")
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "\u00A7b[EvoBank] Accounts: \u00A7e" + EvoBankManager.get().getAccountCount()
                                            + "\u00A7b, ATMs: \u00A7e" + EvoBankManager.get().getAtmCount()
                            ), false);
                            return 1;
                        }))
                .then(Commands.literal("card")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            EvoBankManager.get().giveCard(target);
                                            context.getSource().sendSuccess(() -> Component.literal("\u00A7a[EvoBank] Issued a bank card for \u00A7e" + target.getGameProfile().getName() + "\u00A7a."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("service")
                        .then(Commands.literal("pin")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return EvoBankManager.get().resetPinForFee(player) ? 1 : 0;
                                }))
                        .then(Commands.literal("card")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return EvoBankManager.get().replaceCardForFee(player) ? 1 : 0;
                                })))
                .then(Commands.literal("npc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    Villager banker = EntityType.VILLAGER.create(player.level());
                                    if (banker == null) {
                                        player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Could not create banker NPC."));
                                        return 0;
                                    }

                                    banker.setPos(player.getX(), player.getY(), player.getZ());
                                    banker.setYRot(player.getYRot());
                                    banker.setYBodyRot(player.getYRot());
                                    banker.setYHeadRot(player.getYRot());
                                    banker.setNoAi(true);
                                    banker.setInvulnerable(true);
                                    banker.setPersistenceRequired();
                                    banker.setSilent(true);
                                    banker.addTag(EvoBankManager.BANKER_TAG);
                                    banker.setCustomName(Component.literal("\u00A76\u00A7lBanker"));
                                    banker.setCustomNameVisible(true);

                                    player.level().addFreshEntity(banker);
                                    player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Banker NPC spawned."));
                                    return 1;
                                }))
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    AABB area = new AABB(player.blockPosition()).inflate(4.0D);
                                    List<Villager> bankers = player.level().getEntitiesOfClass(Villager.class, area,
                                            villager -> villager.getTags().contains(EvoBankManager.BANKER_TAG));
                                    if (bankers.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] No banker NPC found within 4 blocks."));
                                        return 0;
                                    }

                                    Villager closest = bankers.stream()
                                            .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                                            .orElse(bankers.get(0));
                                    closest.discard();
                                    player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Banker NPC removed."));
                                    return 1;
                                })))
                .then(Commands.literal("atm")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BlockPos pos = getLookedAtBlock(player);
                                    if (pos == null) {
                                        player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Look at a block within 6 blocks first."));
                                        return 0;
                                    }

                                    boolean created = EvoBankManager.get().addAtm(player.level(), pos);
                                    player.sendSystemMessage(Component.literal(created
                                            ? "\u00A7a[EvoBank] ATM registered at the selected block."
                                            : "\u00A7e[EvoBank] This block was already an ATM."));
                                    return 1;
                                }))
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BlockPos pos = getLookedAtBlock(player);
                                    if (pos == null) {
                                        player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Look at an ATM block within 6 blocks first."));
                                        return 0;
                                    }

                                    boolean removed = EvoBankManager.get().removeAtm(player.level(), pos);
                                    player.sendSystemMessage(Component.literal(removed
                                            ? "\u00A7a[EvoBank] ATM removed."
                                            : "\u00A7c[EvoBank] That block is not registered as an ATM."));
                                    return removed ? 1 : 0;
                                })))
        );
    }

    private static BlockPos getLookedAtBlock(ServerPlayer player) {
        HitResult result = player.pick(6.0D, 0.0F, false);
        if (result.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) result).getBlockPos();
    }
}
