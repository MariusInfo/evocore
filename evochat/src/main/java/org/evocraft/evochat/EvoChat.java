package org.evocraft.evochat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evochat.chat.ChatManager;
import org.evocraft.evochat.commands.ChatCommands;

@Mod(EvoChat.MODID)
public class EvoChat {
    public static final String MODID = "evochat";

    public EvoChat() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ChatCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ChatManager.initializeDB();
    }
}
