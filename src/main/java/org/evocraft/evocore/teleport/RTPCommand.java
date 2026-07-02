package org.evocraft.evocore.teleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RTPCommand {

    // Salvăm momentul în care jucătorul a folosit comanda
    private static final Map<UUID, Long> rtpCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 30 * 60 * 1000; // 30 minute în milisecunde

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtp")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    UUID uuid = player.getUUID();
                    long currentTime = System.currentTimeMillis();

                    // Verificăm Cooldown-ul (Dacă nu e admin)
                    if (!player.hasPermissions(2) && rtpCooldowns.containsKey(uuid)) {
                        long timePassed = currentTime - rtpCooldowns.get(uuid);
                        if (timePassed < COOLDOWN_MS) {
                            long secondsLeft = (COOLDOWN_MS - timePassed) / 1000;
                            long minutes = secondsLeft / 60;
                            long seconds = secondsLeft % 60;
                            player.sendSystemMessage(Component.literal("§c⏳ Trebuie să aștepți " + minutes + "m și " + seconds + "s pentru un nou RTP!"));
                            return 0;
                        }
                    }

                    // Pornește teleportarea asincronă cu 5 secunde așteptare
                    TeleportManager.queueTeleport(player, "Teleportare Aleatorie (RTP)", () -> {
                        ServerLevel level = player.serverLevel();
                        Random random = new Random();

                        int targetX = 0, targetZ = 0, targetY = 0;
                        BlockPos finalPos = null;

                        player.sendSystemMessage(Component.literal("§e[EvoCore] Se caută o locație sigură..."));

                        // Încercăm de maxim 10 ori să găsim pământ uscat și sigur
                        for (int i = 0; i < 10; i++) {
                            targetX = random.nextInt(10000) - 5000; // Între -5000 și 5000
                            targetZ = random.nextInt(10000) - 5000;

                            // ==============================================================
                            // FIX CRITIC: Forțăm serverul să asigure stadiul FULL al chunk-ului.
                            // Fără acest parametru, serverul poate da un chunk virtual.
                            // ==============================================================
                            level.getChunk(targetX >> 4, targetZ >> 4, ChunkStatus.FULL, true);

                            // Acum luăm înălțimea REALĂ, garantat calculată
                            targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);

                            // Respingem orice e mai jos de nivelul mării - poate fi un chunk error (sau void)
                            if (targetY <= level.getMinBuildHeight() + 5) {
                                continue;
                            }

                            BlockPos checkPos = new BlockPos(targetX, targetY - 1, targetZ);
                            BlockState blockBelow = level.getBlockState(checkPos);
                            BlockState blockAt = level.getBlockState(new BlockPos(targetX, targetY, targetZ));
                            BlockState blockAbove = level.getBlockState(new BlockPos(targetX, targetY + 1, targetZ));

                            // Verificăm să calce pe ceva solid (nu aer, nu lavă, nu apă)
                            if (blockBelow.isAir() || blockBelow.getFluidState().isSource()) {
                                continue;
                            }

                            // Verificăm să nu se sufoce (blocurile 1 și 2 de deasupra picioarelor trebuie să fie aer)
                            if (!blockAt.isAir() || !blockAbove.isAir()) {
                                continue;
                            }

                            finalPos = new BlockPos(targetX, targetY, targetZ);
                            break; // Am găsit locația perfectă
                        }

                        if (finalPos != null) {
                            // Teleportăm jucătorul
                            player.teleportTo(level, finalPos.getX() + 0.5, finalPos.getY(), finalPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                            player.sendSystemMessage(Component.literal("§a✔ Ai fost teleportat într-o zonă aleatorie!"));
                            rtpCooldowns.put(uuid, System.currentTimeMillis()); // Punem cooldown-ul
                        } else {
                            player.sendSystemMessage(Component.literal("§c✖ Nu am putut găsi o locație sigură (doar oceane/zone invalide). Încearcă din nou!"));
                        }
                    });

                    return 1;
                })
        );
    }
}