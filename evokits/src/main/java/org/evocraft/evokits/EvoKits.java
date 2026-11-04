package org.evocraft.evokits;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evokits.commands.KitCommands;
import org.evocraft.evokits.data.KitManager;

@Mod(EvoKits.MODID)
public class EvoKits {
    public static final String MODID = "evokits";

    public EvoKits() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        KitCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        KitManager.get();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        KitManager.get().save();
    }
}
