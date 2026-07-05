package org.evocraft.evocore;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import org.evocraft.evocore.commands.*;
import org.evocraft.evocore.bank.EvoBankCommands;
import org.evocraft.evocore.bank.EvoBankAtmMenu;
import org.evocraft.evocore.bank.EvoBankManager;
import org.evocraft.evocore.client.EvoBankAtmScreen;
import org.evocraft.evocore.vote.EvoVotifier;
import org.evocraft.evocore.data.*;
import org.evocraft.evocore.network.PacketHandler;
import org.evocraft.evocore.database.DatabaseManager;
import org.evocraft.evocore.teleport.HomeManager;
import org.evocraft.evocore.teleport.RTPCommand;
import org.evocraft.evocore.chat.ChatManager;
import org.evocraft.evocore.chat.ChatCommands;
import org.evocraft.evocore.npc.TopNPC;
import org.evocraft.evocore.npc.TopNPCRenderer;

@Mod(EvoCore.MODID)
public class EvoCore {

    public static final String MODID = "evocore";

    // --- REGISTRUL PENTRU NPC ---
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<TopNPC>> TOP_NPC = ENTITIES.register("top_npc",
            () -> EntityType.Builder.of(TopNPC::new, MobCategory.MISC).sized(0.6f, 1.8f).build("top_npc"));
    public static final RegistryObject<MenuType<EvoBankAtmMenu>> EVO_BANK_ATM_MENU = MENUS.register("evobank_atm",
            () -> IForgeMenuType.create((containerId, inventory, data) -> new EvoBankAtmMenu(containerId, inventory)));
    public static final RegistryObject<Item> EVOBANK_CARD = ITEMS.register("evobank_card",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EVOCASH_1 = ITEMS.register("evocash_1",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_5 = ITEMS.register("evocash_5",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_10 = ITEMS.register("evocash_10",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_50 = ITEMS.register("evocash_50",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_100 = ITEMS.register("evocash_100",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_500 = ITEMS.register("evocash_500",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_1000 = ITEMS.register("evocash_1000",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_5000 = ITEMS.register("evocash_5000",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> EVOCASH_10000 = ITEMS.register("evocash_10000",
            () -> new Item(new Item.Properties()));

    public EvoCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Înregistrăm entitățile în sistemul Forge
        ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener((FMLCommonSetupEvent event) -> setup(event));
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    @Mod.EventBusSubscriber(modid = EvoCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void onAttributeCreate(EntityAttributeCreationEvent event) {
            // Asignăm viața și atributele NPC-ului
            event.put(TOP_NPC.get(), TopNPC.createAttributes().build());
        }

    }

    @Mod.EventBusSubscriber(modid = EvoCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // Asignăm cum va arăta NPC-ul (Model + Skin + Hologramă)
            event.registerEntityRenderer(EvoCore.TOP_NPC.get(), TopNPCRenderer::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> MenuScreens.register(EvoCore.EVO_BANK_ATM_MENU.get(), EvoBankAtmScreen::new));
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        TempBanCommand.register(event.getDispatcher());
        AdminCommands.register(event.getDispatcher());
        FlyCommand.register(event.getDispatcher());
        GodCommand.register(event.getDispatcher());
        InvseeCommand.register(event.getDispatcher());
        RTPCommand.register(event.getDispatcher());
        BroadcastCommand.register(event.getDispatcher());
        ChatCommands.register(event.getDispatcher());

        StaffGmCommand.register(event.getDispatcher());
        StaffTpCommand.register(event.getDispatcher());
        WarnCommands.register(event.getDispatcher());
        org.evocraft.evocore.vote.VoteCommands.register(event.getDispatcher());
        EvoBankCommands.register(event.getDispatcher());

        // Comanda pentru NPC-uri Top
        TopNPCCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        EvoVotifier.start();
        TranslationManager.initialize();
        DatabaseManager.initialize();
        ChatManager.initializeDB();
        WarnManager.initializeDB();
        EconomyManager.initialize();
        WarpManager.get();
        PlayerStatsManager.initialize();
        KitManager.get();
        CrateKeyManager.get();
        EvoBankManager.initialize();
        HomeManager.initialize();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PlayerStatsManager.get().save();
        EvoVotifier.stop();
        KitManager.get().save();
        EvoBankManager.get().save();
        WarpManager.get().save();
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
                        }

                        if (org.evocraft.evocore.data.PlayerStatsManager.get() != null) {
                            org.evocraft.evocore.data.PlayerStatsManager.get().syncClient(player.getUUID());
                        }

                        if (HomeManager.get() != null) {
                            HomeManager.get().syncHomes(player);
                        }
                    } catch (Exception e) {
                        System.err.println("[EvoCore] EROARE PRINSĂ LA LOGIN (Homes/Economy/Stats): Serverul a fost salvat!");
                        e.printStackTrace();
                    }
                }));
            }
        }
    }
}
