package org.evocraft.evocore.bank;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EvoBankAtmMenu extends ChestMenu {
    private static final int ROWS = 3;
    private static final int CONTAINER_SIZE = ROWS * 9;
    private static final int[] BUTTON_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] AMOUNTS = {1, 5, 10, 50, 100, 1000, 10000};

    private final Container atmContainer;

    public EvoBankAtmMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE));
    }

    private EvoBankAtmMenu(int containerId, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_9x3, containerId, playerInventory, container, ROWS);
        this.atmContainer = container;
        populate();
    }

    private void populate() {
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.setHoverName(Component.literal(" "));
        for (int slot = 0; slot < CONTAINER_SIZE; slot++) {
            atmContainer.setItem(slot, filler.copy());
        }

        for (int i = 0; i < BUTTON_SLOTS.length; i++) {
            atmContainer.setItem(BUTTON_SLOTS[i], createWithdrawButton(AMOUNTS[i]));
        }
    }

    private ItemStack createWithdrawButton(int amount) {
        ItemStack item = new ItemStack(Items.PAPER);
        EvoBankManager.applyCashModel(item, amount);
        item.setHoverName(Component.literal("\u00A76Withdraw \u00A7e" + EvoBankManager.formatAmount(amount) + " Evo Cash"));
        item.getOrCreateTag().putInt("EvoBankWithdrawAmount", amount);
        return item;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < CONTAINER_SIZE) {
            int amount = amountForSlot(slotId);
            if (amount > 0 && player instanceof ServerPlayer serverPlayer) {
                EvoBankManager.get().withdraw(serverPlayer, amount);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private int amountForSlot(int slot) {
        for (int i = 0; i < BUTTON_SLOTS.length; i++) {
            if (BUTTON_SLOTS[i] == slot) return AMOUNTS[i];
        }
        return 0;
    }
}
