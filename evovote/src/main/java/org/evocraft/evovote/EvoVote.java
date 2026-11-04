package org.evocraft.evovote;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(EvoVote.MODID)
public class EvoVote {
    public static final String MODID = "evovote";

    public EvoVote() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VoteConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        org.evocraft.evovote.vote.VoteCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        org.evocraft.evovote.vote.EvoVotifier.start();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        org.evocraft.evovote.vote.EvoVotifier.stop();
    }
}
