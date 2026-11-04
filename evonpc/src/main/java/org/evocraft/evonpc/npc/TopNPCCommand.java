package org.evocraft.evonpc.npc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.evocraft.evonpc.EvoNPC;
import org.evocraft.evonpc.npc.TopNPC;
import org.evocraft.evonpc.npc.TopManager;

import java.util.List;

public class TopNPCCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("topnpc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("category", StringArgumentType.word())
                                .then(Commands.argument("rank", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String category = normalizeCategory(StringArgumentType.getString(context, "category").toLowerCase());
                                            int rank = IntegerArgumentType.getInteger(context, "rank");

                                            if (!category.equals("money") && !category.equals("hours") && !category.equals("claims") && !category.equals("plots") && !category.equals("kills") && !category.equals("deaths")) {
                                                player.sendSystemMessage(Component.literal("§c[!] Valid categories: money, hours, claims, plots, kills, deaths."));
                                                return 0;
                                            }

                                            TopNPC npc = EvoNPC.TOP_NPC.get().create(player.level());
                                            if (npc != null) {
                                                npc.setPos(player.getX(), player.getY(), player.getZ());
                                                npc.setYRot(player.getYRot());
                                                npc.setYBodyRot(player.getYRot());
                                                npc.setYHeadRot(player.getYRot());

                                                npc.setCategory(category);
                                                npc.setRank(rank);

                                                if (TopManager.topData.containsKey(category) && TopManager.topData.get(category).containsKey(rank)) {
                                                    TopManager.TopEntry entry = TopManager.topData.get(category).get(rank);
                                                    npc.setPlayerName(entry.name);
                                                    npc.setDisplayValue(entry.displayValue);
                                                }

                                                player.level().addFreshEntity(npc);
                                                player.sendSystemMessage(Component.literal("§a[✔] Spawned NPC for " + displayCategory(category) + " - Rank " + rank));
                                            }

                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            AABB box = new AABB(player.blockPosition()).inflate(3.0D);

                            List<TopNPC> npcs = player.level().getEntitiesOfClass(TopNPC.class, box);
                            if (npcs.isEmpty()) {
                                player.sendSystemMessage(Component.literal("§c[!] No NPC found within 3 blocks."));
                                return 0;
                            }

                            TopNPC closest = npcs.get(0);
                            closest.discard();
                            player.sendSystemMessage(Component.literal("§a[✔] NPC removed successfully!"));
                            return 1;
                        })
                )
                .then(Commands.literal("refresh")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            TopManager.forceUpdateNow(player.getServer());
                            player.sendSystemMessage(Component.literal("§a[✔] Forced leaderboard refresh from the database!"));
                            return 1;
                        })
                )
        );
    }

    private static String displayCategory(String category) {
        return switch (category) {
            case "claims" -> "Protections";
            default -> capitalize(category);
        };
    }

    private static String normalizeCategory(String category) {
        return switch (category) {
            case "bani" -> "money";
            case "ore" -> "hours";
            case "decese" -> "deaths";
            default -> category;
        };
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
