package org.evocraft.evocore.bank;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.network.PacketHandler;

import java.util.UUID;

public class EvoBankAtmMenu extends AbstractContainerMenu {
    public static final int CARD_SLOT = 0;
    public static final int DEPOSIT_SLOT = 1;
    public static final int BANK_SLOT_COUNT = 2;
    public static final int CARD_SLOT_X = 55;
    public static final int CARD_SLOT_Y = 78;
    public static final int DEPOSIT_SLOT_X = 55;
    public static final int DEPOSIT_SLOT_Y = 133;
    public static final int INVENTORY_X = 95;
    public static final int INVENTORY_Y = 191;
    public static final int HOTBAR_Y = 249;
    public static final int[] WITHDRAW_AMOUNTS = {1, 5, 10, 50, 100, 1000, 10000};

    private final Inventory playerInventory;
    private final Container bankContainer;
    private boolean depositSlotVisible = false;
    private UUID authenticatedAccount;
    private String authenticatedCardId = "";

    public EvoBankAtmMenu(int containerId, Inventory playerInventory) {
        super(EvoCore.EVO_BANK_ATM_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.bankContainer = new SimpleContainer(BANK_SLOT_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                EvoBankAtmMenu.this.slotsChanged(this);
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };

        addSlot(new Slot(bankContainer, CARD_SLOT, CARD_SLOT_X, CARD_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EvoBankManager.isBankCardItem(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addSlot(new Slot(bankContainer, DEPOSIT_SLOT, DEPOSIT_SLOT_X, DEPOSIT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EvoBankManager.isCashItem(stack);
            }

            @Override
            public boolean isActive() {
                return !playerInventory.player.level().isClientSide() || EvoBankAtmMenu.this.depositSlotVisible;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        syncState("", true);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == bankContainer) {
            validateAuthentication();
            syncState("", true);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < BANK_SLOT_COUNT) {
            if (!moveItemStackTo(stack, BANK_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (EvoBankManager.isBankCardItem(stack)) {
            if (!moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (EvoBankManager.isCashItem(stack)) {
            if (!moveItemStackTo(stack, DEPOSIT_SLOT, DEPOSIT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            for (int i = 0; i < bankContainer.getContainerSize(); i++) {
                ItemStack stack = bankContainer.removeItemNoUpdate(i);
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(stack);
                }
            }
        }
    }

    public void handlePin(ServerPlayer player, String pin) {
        EvoBankManager.BankAccount account = getCardAccount();
        if (account == null) {
            clearAuthentication();
            syncState("Insert a valid EvoBank card first.", false);
            return;
        }

        if (!EvoBankManager.get().isValidPin(pin)) {
            clearAuthentication();
            syncState("PIN must contain 4-6 digits.", false);
            return;
        }

        if (!EvoBankManager.get().hasPin(account)) {
            if (EvoBankManager.get().setPin(account, pin)) {
                authenticate(account);
                syncState("PIN set. Card unlocked.", true);
            } else {
                clearAuthentication();
                syncState("Could not set PIN.", false);
            }
            return;
        }

        if (EvoBankManager.get().verifyPin(account, pin)) {
            authenticate(account);
            syncState("PIN accepted. Account unlocked.", true);
        } else {
            clearAuthentication();
            syncState("Wrong PIN.", false);
        }
    }

    public void handleWithdraw(ServerPlayer player, int amount) {
        EvoBankManager.BankAccount account = getAuthenticatedAccount();
        if (account == null) {
            syncState("Unlock a card with PIN first.", false);
            return;
        }

        if (!EvoBankManager.get().isAllowedWithdrawAmount(amount)) {
            syncState("Invalid withdrawal amount.", false);
            return;
        }

        if (EvoBankManager.get().withdrawFromAccount(player, account, amount)) {
            syncState("Withdrawn " + EvoBankManager.formatAmount(amount) + " Evo Cash.", true);
        } else {
            syncState("Not enough funds.", false);
        }
    }

    public void handleDeposit(ServerPlayer player) {
        EvoBankManager.BankAccount account = getAuthenticatedAccount();
        if (account == null) {
            syncState("Unlock a card with PIN first.", false);
            return;
        }

        ItemStack stack = bankContainer.getItem(DEPOSIT_SLOT);
        double value = EvoBankManager.getCashValue(stack);
        if (value <= 0.0D) {
            syncState("Drag Evo Cash into the deposit slot.", false);
            return;
        }

        bankContainer.setItem(DEPOSIT_SLOT, ItemStack.EMPTY);
        if (EvoBankManager.get().depositToAccount(account, value)) {
            syncState("Deposited " + EvoBankManager.formatAmount(value) + " Evo Cash.", true);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
            syncState("Deposit failed.", false);
        }
    }

    public void handleTransfer(ServerPlayer player, String targetName, double amount) {
        EvoBankManager.BankAccount from = getAuthenticatedAccount();
        if (from == null) {
            syncState("Unlock a card with PIN first.", false);
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            syncState("Enter a valid transfer amount.", false);
            return;
        }

        EvoBankManager.BankAccount to = EvoBankManager.get().findAccountByName(targetName);
        if (to == null) {
            syncState("Target player has no EvoBank account.", false);
            return;
        }

        if (from.ownerUuid.equals(to.ownerUuid)) {
            syncState("You cannot transfer to the same account.", false);
            return;
        }

        if (EvoBankManager.get().transfer(from, to, amount)) {
            ServerPlayer target = player.getServer() == null ? null : player.getServer().getPlayerList().getPlayer(to.ownerUuid);
            if (target != null) {
                target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00A7a[EvoBank] You received \u00A7e" + EvoBankManager.formatAmount(amount)
                                + " Evo Cash\u00A7a from \u00A7f" + from.ownerName + "\u00A7a."
                ));
            }
            syncState("Transferred " + EvoBankManager.formatAmount(amount) + " Evo Cash to " + to.ownerName + ".", true);
        } else {
            syncState("Transfer failed. Check balance.", false);
        }
    }

    public void syncState(String message, boolean positive) {
        if (!(playerInventory.player instanceof ServerPlayer player)) return;

        ItemStack card = bankContainer.getItem(CARD_SLOT);
        boolean hasCard = !card.isEmpty();
        EvoBankManager.BankAccount account = EvoBankManager.get().getAccountByCard(card);
        boolean cardValid = account != null;
        boolean authenticated = isAuthenticated(account);
        double balance = authenticated ? EvoBankManager.get().getAccountBalance(account) : 0.0D;
        String ownerName = account == null || account.ownerName == null ? "" : account.ownerName;
        boolean hasPin = account != null && EvoBankManager.get().hasPin(account);

        PacketHandler.sendToPlayer(new PacketHandler.S2C_EvoBankAtmState(
                hasCard,
                cardValid,
                hasPin,
                authenticated,
                ownerName,
                balance,
                message == null ? "" : message,
                positive
        ), player);
    }

    private void validateAuthentication() {
        EvoBankManager.BankAccount account = getCardAccount();
        if (!isAuthenticated(account)) {
            clearAuthentication();
        }
    }

    private EvoBankManager.BankAccount getCardAccount() {
        return EvoBankManager.get().getAccountByCard(bankContainer.getItem(CARD_SLOT));
    }

    private EvoBankManager.BankAccount getAuthenticatedAccount() {
        EvoBankManager.BankAccount account = getCardAccount();
        return isAuthenticated(account) ? account : null;
    }

    private boolean isAuthenticated(EvoBankManager.BankAccount account) {
        return account != null
                && authenticatedAccount != null
                && account.ownerUuid.equals(authenticatedAccount)
                && authenticatedCardId.equals(EvoBankManager.get().getCardId(bankContainer.getItem(CARD_SLOT)));
    }

    private void authenticate(EvoBankManager.BankAccount account) {
        authenticatedAccount = account.ownerUuid;
        authenticatedCardId = EvoBankManager.get().getCardId(bankContainer.getItem(CARD_SLOT));
    }

    private void clearAuthentication() {
        authenticatedAccount = null;
        authenticatedCardId = "";
    }

    public void setDepositSlotVisible(boolean depositSlotVisible) {
        this.depositSlotVisible = depositSlotVisible;
    }
}
