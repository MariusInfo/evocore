package org.evocraft.evonpc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.evocraft.evonpc.npc.TopNPC;
import org.evocraft.evonpc.npc.TopNPCRenderer;

@Mod(EvoNPC.MODID)
public class EvoNPC {
    public static final String MODID = "evonpc";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final RegistryObject<EntityType<TopNPC>> TOP_NPC = ENTITIES.register("top_npc",
            () -> EntityType.Builder.<TopNPC>of(TopNPC::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("top_npc"));

    public EvoNPC() {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        MinecraftForge.EVENT_BUS.register(this);
        bus.addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {}

    @SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        org.evocraft.evonpc.npc.TopNPCCommand.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = EvoNPC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void onAttributeCreate(EntityAttributeCreationEvent event) {
            event.put(TOP_NPC.get(), TopNPC.createAttributes().build());
        }
    }

    @Mod.EventBusSubscriber(modid = EvoNPC.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TOP_NPC.get(), TopNPCRenderer::new);
        }
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) { }
    }
}

