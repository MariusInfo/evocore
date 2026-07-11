package org.evocraft.evocore.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.data.EventLogManager;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class EventLogEvents {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EventLogManager manager = EventLogManager.get();
        if (manager != null) {
            manager.onServerTick();
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isBlockBreakEnabled() || event.isCanceled()) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            BlockState state = event.getState();
            if (!state.isAir()) {
                EventLogManager.record(player, "BLOCK_BREAK", blockId(state), event.getPos(), "block break");
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isBlockPlaceEnabled() || event.isCanceled()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            BlockState state = event.getPlacedBlock();
            if (!state.isAir()) {
                EventLogManager.record(player, "BLOCK_PLACE", blockId(state), event.getPos(), "block place");
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isBlockInteractEnabled() || event.isCanceled()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        boolean trackedBlock = isTrackedInteractionBlock(level, pos, state);
        boolean carryOnItem = isCarryOnItem(event.getItemStack());
        if (!trackedBlock && !carryOnItem) {
            return;
        }

        String action = carryOnItem ? "CARRYON_INTERACT" : "BLOCK_INTERACT";
        EventLogManager.record(player, action, blockId(state), pos, event.getHand().name());
    }

    @SubscribeEvent
    public static void onItemDrop(ItemTossEvent event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isItemDropEnabled() || event.isCanceled()) {
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemEntity itemEntity = event.getEntity();
            ItemStack stack = itemEntity.getItem();
            EventLogManager.record(player, "ITEM_DROP", itemId(stack), itemEntity.blockPosition(),
                    "count=" + stack.getCount());
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isItemPickupEnabled() || event.isCanceled()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ItemEntity itemEntity = event.getItem();
            ItemStack stack = itemEntity.getItem();
            EventLogManager.record(player, "ITEM_PICKUP", itemId(stack), itemEntity.blockPosition(),
                    "count=" + stack.getCount());
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null || !manager.isContainerOpenEnabled()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            AbstractContainerMenu menu = event.getContainer();
            EventLogManager.record(player, "CONTAINER_OPEN", menuId(menu), player.blockPosition(), "container open");
        }
    }

    private static boolean isTrackedInteractionBlock(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) {
            return true;
        }

        String id = blockId(state).toLowerCase(Locale.ROOT);
        return id.contains("chest")
                || id.contains("barrel")
                || id.contains("shulker")
                || id.contains("hopper")
                || id.contains("furnace")
                || id.contains("dispenser")
                || id.contains("dropper")
                || id.contains("brewing_stand");
    }

    private static boolean isCarryOnItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = itemId(stack).toLowerCase(Locale.ROOT);
        return id.contains("carryon") || id.contains("carry_on");
    }

    private static String blockId(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null ? key.toString() : state.getBlock().getDescriptionId();
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null ? key.toString() : stack.getItem().getDescriptionId();
    }

    private static String menuId(AbstractContainerMenu menu) {
        if (menu == null || menu.getType() == null) {
            return "unknown";
        }
        ResourceLocation key = ForgeRegistries.MENU_TYPES.getKey(menu.getType());
        return key != null ? key.toString() : menu.getType().toString();
    }
}
