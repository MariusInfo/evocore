package org.evocraft.evocore;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.evocraft.evocore.data.*;
import org.evocraft.evocore.network.PacketHandler;
import org.evocraft.evocore.database.DatabaseManager;

@Mod(EvoCore.MODID)
public class EvoCore {
    public static final String MODID = "evocore";

    public EvoCore() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    @SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        org.evocraft.evocore.database.MigrateDatabaseCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        TranslationManager.initialize();
        DatabaseManager.initialize();
        EventLogManager.initialize();
        EconomyManager.initialize();
        PlayerStatsManager.initialize();
        CrateKeyManager.get();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PlayerStatsManager.get().save();
        EventLogManager.shutdown();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            String playerName = player.getName().getString();

            if (PlayerStatsManager.get() != null) {
                PlayerStatsManager.get().updateName(uuid, playerName);
            }

            if (player.getServer() != null) {
                player.getServer().tell(new net.minecraft.server.TickTask(player.getServer().getTickCount() + 10, () -> {
                    try {
                        if (PlayerStatsManager.get() != null) {
                            PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(uuid);
                            String realRank = PlayerStatsManager.get().getLuckPermsRank(uuid);
                            PacketHandler.sendToPlayer(new PacketHandler.S2C_SyncStats(stats.kills, stats.deaths, stats.playtimeSeconds, realRank), player);
                            org.evocraft.evocore.data.PlayerStatsManager.get().syncClient(player.getUUID());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));
            }
        }
    }
}
