package org.evocraft.evovote.vote;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evovote.EvoVote;
import org.evocraft.evovote.VoteConfig;

@Mod.EventBusSubscriber(modid = EvoVote.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoteManager {

    private static int tickCounter = 0;
    // 20 ticks = 1 second. 60 seconds * 30 minutes = 36000 ticks
    private static final int REMINDER_INTERVAL = 20 * 60 * 30;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;

            if (tickCounter >= REMINDER_INTERVAL) {
                tickCounter = 0;

                MinecraftServer server = event.getServer();
                if (server != null && server.getPlayerCount() > 0) {
                    String voteUrl = VoteConfig.VOTE_URL.get();
                    String keyName = VoteConfig.VOTE_KEY_NAME.get();

                    Component link = Component.literal("§a§l[CLICK HERE TO VOTE]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, voteUrl))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eOpen vote link!"))));

                    server.getPlayerList().broadcastSystemMessage(Component.literal("§8§m----------------------------------------"), false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§e⭐ §lWANT TO SUPPORT THE SERVER AND EARN REWARDS? §e⭐"), false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§fVote daily and receive §a1x " + keyName + " Key§f!"), false);
                    server.getPlayerList().broadcastSystemMessage(link, false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§8§m----------------------------------------"), false);
                }
            }
        }
    }
}
