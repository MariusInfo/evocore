package org.evocraft.evocore.bank;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.network.PacketHandler;

import java.util.Locale;

public class EvoBankerMenu extends AbstractContainerMenu {
    public boolean hasAccount;
    public boolean createdNow;
    public String ownerName;
    public double balance;
    public String message;
    public boolean positive;

    public EvoBankerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, false, false, "", 0.0D, "", true);
    }

    public EvoBankerMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory,
                data.readBoolean(),
                data.readBoolean(),
                data.readUtf(32),
                data.readDouble(),
                data.readUtf(128),
                data.readBoolean());
    }

    public EvoBankerMenu(int containerId, Inventory inventory, boolean hasAccount, boolean createdNow,
                         String ownerName, double balance, String message, boolean positive) {
        super(EvoCore.EVO_BANKER_MENU.get(), containerId);
        updateState(hasAccount, createdNow, ownerName, balance, message, positive);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public void handleService(ServerPlayer player, String service) {
        String normalized = service == null ? "" : service.toLowerCase(Locale.ROOT);
        boolean success;
        String response;
        if ("pin".equals(normalized)) {
            success = EvoBankManager.get().resetPinForFee(player);
            response = success
                    ? "PIN reset paid. Set a new PIN at the ATM."
                    : "PIN reset could not be completed.";
        } else if ("card".equals(normalized)) {
            success = EvoBankManager.get().replaceCardForFee(player);
            response = success
                    ? "New card issued. The old card is now invalid."
                    : "Card replacement could not be completed.";
        } else {
            success = false;
            response = "Unknown banking service.";
        }
        syncFromAccount(player, response, success);
    }

    public void syncFromAccount(ServerPlayer player, String message, boolean positive) {
        EvoBankManager.BankAccount account = EvoBankManager.get().getAccount(player.getUUID());
        boolean accountExists = account != null;
        double currentBalance = accountExists ? EvoBankManager.get().getAccountBalance(account) : 0.0D;
        String currentOwner = accountExists ? account.ownerName : player.getGameProfile().getName();
        updateState(accountExists, false, currentOwner, currentBalance, message, positive);
        PacketHandler.sendToPlayer(new PacketHandler.S2C_EvoBankerState(
                hasAccount, ownerName, balance, this.message, this.positive
        ), player);
    }

    public void updateState(boolean hasAccount, boolean createdNow, String ownerName,
                            double balance, String message, boolean positive) {
        this.hasAccount = hasAccount;
        this.createdNow = createdNow;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.balance = balance;
        this.message = message == null ? "" : message;
        this.positive = positive;
    }
}
