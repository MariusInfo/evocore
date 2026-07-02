package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.npc.TopNPC;
import org.evocraft.evocore.npc.TopManager;

import java.util.List;

public class TopNPCCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("topnpc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("categorie", StringArgumentType.word())
                                .then(Commands.argument("loc", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String categorie = StringArgumentType.getString(context, "categorie").toLowerCase();
                                            int loc = IntegerArgumentType.getInteger(context, "loc");

                                            if (!categorie.equals("bani") && !categorie.equals("ore") && !categorie.equals("claims") && !categorie.equals("plots") && !categorie.equals("kills") && !categorie.equals("decese")) {
                                                player.sendSystemMessage(Component.literal("§c[!] Categoriile valide sunt: bani, ore, claims, plots, kills, decese."));
                                                return 0;
                                            }

                                            TopNPC npc = EvoCore.TOP_NPC.get().create(player.level());
                                            if (npc != null) {
                                                npc.setPos(player.getX(), player.getY(), player.getZ());
                                                npc.setYRot(player.getYRot());
                                                npc.setYBodyRot(player.getYRot());
                                                npc.setYHeadRot(player.getYRot());

                                                npc.setCategory(categorie);
                                                npc.setRank(loc);

                                                // Îi aplicăm datele pe loc, ca să nu aștepți 5 minute!
                                                if (TopManager.topData.containsKey(categorie) && TopManager.topData.get(categorie).containsKey(loc)) {
                                                    TopManager.TopEntry entry = TopManager.topData.get(categorie).get(loc);
                                                    npc.setPlayerName(entry.name);
                                                    npc.setDisplayValue(entry.displayValue);
                                                }

                                                player.level().addFreshEntity(npc);
                                                player.sendSystemMessage(Component.literal("§a[✔] Ai spawnat NPC-ul pentru " + categorie + " - Locul " + loc));
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
                                player.sendSystemMessage(Component.literal("§c[!] Nu s-a găsit niciun NPC în raza de 3 blocuri."));
                                return 0;
                            }

                            TopNPC closest = npcs.get(0);
                            closest.discard();
                            player.sendSystemMessage(Component.literal("§a[✔] NPC șters cu succes!"));
                            return 1;
                        })
                )
                .then(Commands.literal("refresh")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            TopManager.forceUpdateNow(player.getServer());
                            player.sendSystemMessage(Component.literal("§a[✔] Ai forțat actualizarea clasamentului din baza de date!"));
                            return 1;
                        })
                )
        );
    }
}