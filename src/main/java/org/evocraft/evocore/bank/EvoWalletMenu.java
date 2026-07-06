package org.evocraft.evocore.bank;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evocore.EvoCore;

import java.util.List;
import java.util.UUID;

public class EvoWalletMenu extends AbstractContainerMenu {
    public static final int WALLET_SLOT_COUNT = 10;
    public static final int CARD_SLOT = 0;
    public static final int CASH_SLOT_START = 1;
    public static final int CARD_SLOT_X = 52;
    public static final int CARD_SLOT_Y = 54;
    public static final int CASH_GRID_X = 124;
    public static final int CASH_GRID_Y = 38;
    public static final int INVENTORY_X = 95;
    public static final int INVENTORY_Y = 154;
    public static final int HOTBAR_Y = 212;

    private final Inventory playerInventory;
    private final Container walletContainer;

    public EvoWalletMenu(int containerId, Inventory playerInventory) {
        super(EvoCore.EVO_WALLET_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.walletContainer = new SimpleContainer(WALLET_SLOT_COUNT) {
            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };

        addSlot(new ReadOnlyWalletSlot(walletContainer, CARD_SLOT, CARD_SLOT_X, CARD_SLOT_Y));
        for (int i = 0; i < EvoWalletManager.DENOMINATIONS.length; i++) {
            int col = i % 3;
            int row = i / 3;
            addSlot(new ReadOnlyWalletSlot(walletContainer, CASH_SLOT_START + i, CASH_GRID_X + col * 42, CASH_GRID_Y + row * 31));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        if (playerInventory.player instanceof ServerPlayer player) {
            refreshWalletSlots(player);
            EvoWalletManager.get().syncState(player, "", true);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isWalletSlot(slotId)) {
            if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                handleWalletClick(serverPlayer, slotId, button, clickType);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return ItemStack.EMPTY;
        }

        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (isWalletSlot(index)) {
            if (index == CARD_SLOT) {
                withdrawCardToInventory(serverPlayer);
            } else {
                withdrawCashToInventory(serverPlayer, denominationForWalletSlot(index), EvoWalletManager.PHYSICAL_WITHDRAW_STACK_LIMIT);
            }
            return original;
        }

        if (EvoBankManager.isBankCardItem(stack)) {
            if (depositCardFromStack(serverPlayer, stack)) {
                slot.setChanged();
                return original;
            }
            return ItemStack.EMPTY;
        }

        if (EvoBankManager.isCashItem(stack)) {
            int denomination = EvoBankManager.getCashDenomination(stack);
            int accepted = depositCashFromStack(serverPlayer, denomination, stack);
            if (accepted > 0) {
                slot.setChanged();
                return original;
            }
        }

        return ItemStack.EMPTY;
    }

    private void handleWalletClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
        if (slotId == CARD_SLOT) {
            handleCardSlotClick(player, clickType);
            return;
        }

        int denomination = denominationForWalletSlot(slotId);
        if (denomination <= 0) {
            sendUpdate(player, "Invalid wallet slot.", false);
            return;
        }

        if (clickType == ClickType.QUICK_MOVE) {
            withdrawCashToInventory(player, denomination, EvoWalletManager.PHYSICAL_WITHDRAW_STACK_LIMIT);
            return;
        }

        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            if (!EvoBankManager.isCashItem(carried) || EvoBankManager.getCashDenomination(carried) != denomination) {
                sendUpdate(player, "Place matching Evo Cash in this wallet slot.", false);
                return;
            }

            int accepted = depositCashFromStack(player, denomination, carried);
            if (accepted > 0) {
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            return;
        }

        int requested = button == 1 ? 1 : EvoWalletManager.PHYSICAL_WITHDRAW_STACK_LIMIT;
        int removed = EvoWalletManager.get().removeCash(player.getUUID(), denomination, requested);
        if (removed <= 0) {
            sendUpdate(player, "No " + EvoBankManager.formatAmount(denomination) + " Evo notes in wallet.", false);
            return;
        }

        ItemStack payout = EvoBankManager.get().createCashItem(denomination);
        payout.setCount(removed);
        setCarried(payout);
        sendUpdate(player, "Withdrawn " + removed + " x " + EvoBankManager.formatAmount(denomination) + " Evo.", true);
    }

    private void handleCardSlotClick(ServerPlayer player, ClickType clickType) {
        if (clickType == ClickType.QUICK_MOVE) {
            withdrawCardToInventory(player);
            return;
        }

        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            ItemStack card = EvoWalletManager.get().takeCard(player.getUUID());
            if (card.isEmpty()) {
                sendUpdate(player, "No card stored in wallet.", false);
                return;
            }
            setCarried(card);
            sendUpdate(player, "Card removed from wallet.", true);
            return;
        }

        if (!EvoBankManager.isBankCardItem(carried)) {
            sendUpdate(player, "Only EvoBank cards fit in this slot.", false);
            return;
        }

        if (EvoWalletManager.get().hasCard(player.getUUID())) {
            sendUpdate(player, "Wallet already has a card.", false);
            return;
        }

        ItemStack stored = carried.copy();
        stored.setCount(1);
        if (EvoWalletManager.get().setCard(player.getUUID(), stored)) {
            carried.shrink(1);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            sendUpdate(player, "Card stored in wallet.", true);
        } else {
            sendUpdate(player, "Could not store this card.", false);
        }
    }

    private boolean depositCardFromStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !EvoBankManager.isBankCardItem(stack)) return false;
        if (EvoWalletManager.get().hasCard(player.getUUID())) {
            sendUpdate(player, "Wallet already has a card.", false);
            return false;
        }

        ItemStack stored = stack.copy();
        stored.setCount(1);
        if (!EvoWalletManager.get().setCard(player.getUUID(), stored)) {
            sendUpdate(player, "Could not store this card.", false);
            return false;
        }

        stack.shrink(1);
        sendUpdate(player, "Card stored in wallet.", true);
        return true;
    }

    private int depositCashFromStack(ServerPlayer player, int denomination, ItemStack stack) {
        if (stack.isEmpty() || !EvoWalletManager.isSupportedDenomination(denomination)) {
            sendUpdate(player, "Invalid Evo Cash.", false);
            return 0;
        }

        int current = EvoWalletManager.get().getCashCount(player.getUUID(), denomination);
        int space = EvoWalletManager.CASH_SLOT_LIMIT - current;
        if (space <= 0) {
            sendUpdate(player, "This wallet slot is full.", false);
            return 0;
        }

        int accepted = EvoWalletManager.get().addCash(player.getUUID(), denomination, Math.min(stack.getCount(), space));
        if (accepted <= 0) {
            sendUpdate(player, "This wallet slot is full.", false);
            return 0;
        }

        stack.shrink(accepted);
        sendUpdate(player, "Deposited " + accepted + " x " + EvoBankManager.formatAmount(denomination) + " Evo.", true);
        return accepted;
    }

    private void withdrawCardToInventory(ServerPlayer player) {
        UUID owner = player.getUUID();
        ItemStack card = EvoWalletManager.get().getCard(owner);
        if (card.isEmpty()) {
            sendUpdate(player, "No card stored in wallet.", false);
            return;
        }

        if (!EvoWalletManager.get().canFit(player.getInventory(), card)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoWallet] Your inventory is full."));
            sendUpdate(player, "Inventory full.", false);
            return;
        }

        ItemStack removed = EvoWalletManager.get().takeCard(owner);
        player.getInventory().add(removed);
        sendUpdate(player, "Card moved to inventory.", true);
    }

    private void withdrawCashToInventory(ServerPlayer player, int denomination, int requestedCount) {
        UUID owner = player.getUUID();
        int available = EvoWalletManager.get().getCashCount(owner, denomination);
        int count = Math.min(Math.max(1, requestedCount), available);
        if (count <= 0) {
            sendUpdate(player, "No " + EvoBankManager.formatAmount(denomination) + " Evo notes in wallet.", false);
            return;
        }

        List<ItemStack> payout = EvoWalletManager.get().createPhysicalCashStacks(denomination, count);
        if (!EvoWalletManager.get().canFit(player.getInventory(), payout)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoWallet] Your inventory is full."));
            sendUpdate(player, "Inventory full.", false);
            return;
        }

        int removed = EvoWalletManager.get().removeCash(owner, denomination, count);
        if (removed <= 0) {
            sendUpdate(player, "Withdrawal failed.", false);
            return;
        }

        for (ItemStack stack : payout) {
            player.getInventory().add(stack);
        }
        sendUpdate(player, "Withdrawn " + removed + " x " + EvoBankManager.formatAmount(denomination) + " Evo.", true);
    }

    private void refreshWalletSlots(ServerPlayer player) {
        UUID owner = player.getUUID();
        walletContainer.setItem(CARD_SLOT, EvoWalletManager.get().getCard(owner));
        for (int i = 0; i < EvoWalletManager.DENOMINATIONS.length; i++) {
            int denomination = EvoWalletManager.DENOMINATIONS[i];
            int count = EvoWalletManager.get().getCashCount(owner, denomination);
            walletContainer.setItem(CASH_SLOT_START + i, EvoWalletManager.get().createDisplayCashStack(denomination, count));
        }
        broadcastChanges();
    }

    private void sendUpdate(ServerPlayer player, String message, boolean positive) {
        refreshWalletSlots(player);
        EvoWalletManager.get().syncState(player, message, positive);
    }

    private boolean isWalletSlot(int slotId) {
        return slotId >= 0 && slotId < WALLET_SLOT_COUNT;
    }

    private int denominationForWalletSlot(int slotId) {
        int index = slotId - CASH_SLOT_START;
        if (index < 0 || index >= EvoWalletManager.DENOMINATIONS.length) return 0;
        return EvoWalletManager.DENOMINATIONS[index];
    }

    private static class ReadOnlyWalletSlot extends Slot {
        ReadOnlyWalletSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
