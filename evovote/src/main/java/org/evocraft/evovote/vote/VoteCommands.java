package org.evocraft.evovote.vote;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.network.PacketHandler;
import org.evocraft.evovote.VoteConfig;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoteCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ==========================================
        // COMANDA: /vote (Pentru jucători)
        // ==========================================
        dispatcher.register(Commands.literal("vote")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String voteUrl = VoteConfig.VOTE_URL.get();
                    String keyName = VoteConfig.VOTE_KEY_NAME.get();

                    Component link = Component.literal("§a§l[CLICK HERE TO VOTE]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, voteUrl))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eOpen vote link!"))));

                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));
                    player.sendSystemMessage(Component.literal("§e⭐ §lVOTE FOR THE SERVER §e⭐"));
                    player.sendSystemMessage(link);
                    player.sendSystemMessage(Component.literal("§7(Reward: §a" + keyName + " Key§7)"));
                    player.sendSystemMessage(Component.literal("§8§m----------------------------------------"));

                    return 1;
                })
        );

        // ==========================================
        // COMANDA ASCUNSĂ: /adminvote <nume> (Executată de Votifier din consolă)
        // ==========================================
        dispatcher.register(Commands.literal("adminvote")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.string())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            String keyName = VoteConfig.VOTE_KEY_NAME.get();
                            int keyAmount = VoteConfig.VOTE_KEY_AMOUNT.get();
                            boolean broadcast = VoteConfig.BROADCAST_VOTES.get();

                            context.getSource().getServer().getProfileCache().get(playerName).ifPresentOrElse(profile -> {
                                UUID uuid = profile.getId();

                                CompletableFuture.runAsync(() -> CrateKeyManager.get().addKeys(playerName, keyName, keyAmount));

                                if (broadcast) {
                                    String guiMessage = "§e" + playerName + " §fvoted and received §a" + keyAmount + "x " + keyName + " Key§f!";
                                    Component chatMessage = Component.literal("§8[§a§lVOTE§8] " + guiMessage + " Type §e/vote §ffor the link.");

                                    for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                        player.sendSystemMessage(chatMessage);
                                        PacketHandler.sendToPlayer(new PacketHandler.S2C_BroadcastNotification("§a§lVOTE: §f" + guiMessage), player);
                                    }
                                }

                                ServerPlayer onlinePlayer = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                if (onlinePlayer != null) {
                                    onlinePlayer.sendSystemMessage(Component.literal("§a✔ Thank you for voting! " + keyAmount + "x " + keyName + " key(s) added to your account."));
                                }

                            }, () -> {
                                context.getSource().sendFailure(Component.literal("Player " + playerName + " does not exist in the database."));
                            });

                            return 1;
                        })
                )
        );
    }
}
