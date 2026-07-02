package org.evocraft.evocore.vote;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "evocore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoteManager {

    private static int tickCounter = 0;
    // 20 tick-uri = 1 secundă. 60 secunde * 30 minute = 36000 tick-uri
    private static final int REMINDER_INTERVAL = 20 * 60 * 30;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickCounter++;

            if (tickCounter >= REMINDER_INTERVAL) {
                tickCounter = 0; // Resetăm timer-ul

                MinecraftServer server = event.getServer();
                if (server != null && server.getPlayerCount() > 0) {

                    // Creăm butonul clickabil pentru chat
                    Component link = Component.literal("§a§l[CLICK AICI PENTRU A VOTA]")
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://evocraft.ro/vote.php"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eDeschide link-ul de vot!"))));

                    // Trimitem mesajul
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§8§m----------------------------------------"), false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§e⭐ §lVREI SĂ SUSȚII SERVERUL ȘI SĂ FACI BANI? §e⭐"), false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§fVotează-ne zilnic și primești §d1x Cheie Vote§f!"), false);
                    server.getPlayerList().broadcastSystemMessage(link, false);
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§8§m----------------------------------------"), false);
                }
            }
        }
    }
}