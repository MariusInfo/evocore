package org.evocraft.evoessentials;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.evocraft.evoessentials.commands.*;
import org.evocraft.evoessentials.teleport.RTPCommand;

@Mod("evoessentials")
public class EvoEssentials {
    public static final String MODID = "evoessentials";

    public EvoEssentials() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {}

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        org.evocraft.evoessentials.data.WarnManager.initializeDB();
        org.evocraft.evoessentials.data.WarpManager.get();
        org.evocraft.evoessentials.teleport.HomeManager.initialize();
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        org.evocraft.evoessentials.data.WarpManager.get().save();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TempBanCommand.register(event.getDispatcher());
        AdminCommands.register(event.getDispatcher());
        EconomyCommands.register(event.getDispatcher());
        EventLogCommands.register(event.getDispatcher());
        FlyCommand.register(event.getDispatcher());
        GodCommand.register(event.getDispatcher());
        InvseeCommand.register(event.getDispatcher());
        RTPCommand.register(event.getDispatcher());
        BroadcastCommand.register(event.getDispatcher());
        StaffGmCommand.register(event.getDispatcher());
        StaffTpCommand.register(event.getDispatcher());
        VanishCommand.register(event.getDispatcher());
        WarnCommands.register(event.getDispatcher());
    }
}
