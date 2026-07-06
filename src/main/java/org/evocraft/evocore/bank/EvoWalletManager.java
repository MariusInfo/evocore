package org.evocraft.evocore.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.network.PacketHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EvoWalletManager {
    public static final int DATA_VERSION = 1;
    public static final int CASH_SLOT_LIMIT = 500;
    public static final int PHYSICAL_WITHDRAW_STACK_LIMIT = 64;
    public static final int[] DENOMINATIONS = {1, 5, 10, 50, 100, 500, 1000, 5000, 10000};

    private static EvoWalletManager INSTANCE;

    public static void initialize() {
        if (INSTANCE == null) INSTANCE = new EvoWalletManager();
    }

    public static EvoWalletManager get() {
        if (INSTANCE == null) initialize();
        return INSTANCE;
    }

    public static boolean isSupportedDenomination(int denomination) {
        for (int value : DENOMINATIONS) {
            if (value == denomination) return true;
        }
        return false;
    }

    public static int indexOfDenomination(int denomination) {
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            if (DENOMINATIONS[i] == denomination) return i;
        }
        return -1;
    }

    public static String formatCount(int count) {
        return Integer.toString(Math.max(0, count));
    }

    private final Map<UUID, WalletData> wallets = new HashMap<>();
    private final File file = FMLPaths.CONFIGDIR.get().resolve("evocore/evowallets.nbt").toFile();

    private EvoWalletManager() {
        load();
    }

    public synchronized WalletData getOrCreate(UUID owner) {
        return wallets.computeIfAbsent(owner, ignored -> new WalletData());
    }

    public synchronized ItemStack getCard(UUID owner) {
        return getOrCreate(owner).card.copy();
    }

    public synchronized boolean hasCard(UUID owner) {
        return !getOrCreate(owner).card.isEmpty();
    }

    public synchronized boolean setCard(UUID owner, ItemStack stack) {
        if (!EvoBankManager.isBankCardItem(stack) || hasCard(owner)) return false;
        ItemStack stored = stack.copy();
        stored.setCount(1);
        getOrCreate(owner).card = stored;
        save();
        return true;
    }

    public synchronized ItemStack takeCard(UUID owner) {
        WalletData data = getOrCreate(owner);
        ItemStack card = data.card.copy();
        data.card = ItemStack.EMPTY;
        if (!card.isEmpty()) save();
        return card;
    }

    public synchronized int getCashCount(UUID owner, int denomination) {
        if (!isSupportedDenomination(denomination)) return 0;
        return getOrCreate(owner).cashCounts.getOrDefault(denomination, 0);
    }

    public synchronized int[] getCashCounts(UUID owner) {
        WalletData data = getOrCreate(owner);
        int[] counts = new int[DENOMINATIONS.length];
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            counts[i] = data.cashCounts.getOrDefault(DENOMINATIONS[i], 0);
        }
        return counts;
    }

    public synchronized int addCash(UUID owner, int denomination, int requestedCount) {
        if (!isSupportedDenomination(denomination) || requestedCount <= 0) return 0;
        WalletData data = getOrCreate(owner);
        int current = data.cashCounts.getOrDefault(denomination, 0);
        int accepted = Math.min(requestedCount, CASH_SLOT_LIMIT - current);
        if (accepted <= 0) return 0;
        data.cashCounts.put(denomination, current + accepted);
        save();
        return accepted;
    }

    public synchronized int removeCash(UUID owner, int denomination, int requestedCount) {
        if (!isSupportedDenomination(denomination) || requestedCount <= 0) return 0;
        WalletData data = getOrCreate(owner);
        int current = data.cashCounts.getOrDefault(denomination, 0);
        int removed = Math.min(requestedCount, current);
        if (removed <= 0) return 0;
        int remaining = current - removed;
        if (remaining > 0) {
            data.cashCounts.put(denomination, remaining);
        } else {
            data.cashCounts.remove(denomination);
        }
        save();
        return removed;
    }

    public ItemStack createDisplayCashStack(int denomination, int count) {
        if (count <= 0 || !isSupportedDenomination(denomination)) return ItemStack.EMPTY;
        ItemStack stack = EvoBankManager.get().createCashItem(denomination);
        stack.setCount(Math.min(PHYSICAL_WITHDRAW_STACK_LIMIT, count));
        return stack;
    }

    public List<ItemStack> createPhysicalCashStacks(int denomination, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!isSupportedDenomination(denomination) || count <= 0) return stacks;

        int remaining = count;
        while (remaining > 0) {
            int stackCount = Math.min(PHYSICAL_WITHDRAW_STACK_LIMIT, remaining);
            ItemStack stack = EvoBankManager.get().createCashItem(denomination);
            stack.setCount(stackCount);
            stacks.add(stack);
            remaining -= stackCount;
        }
        return stacks;
    }

    public boolean canFit(Inventory inventory, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        return canFit(inventory, List.of(stack));
    }

    public boolean canFit(Inventory inventory, List<ItemStack> stacks) {
        if (inventory == null || stacks == null) return false;

        List<ItemStack> simulated = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            simulated.add(inventory.getItem(i).copy());
        }

        for (ItemStack original : stacks) {
            if (original == null || original.isEmpty()) continue;
            int leftToAdd = original.getCount();

            for (ItemStack slot : simulated) {
                if (leftToAdd <= 0) break;
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, original)) {
                    int add = Math.min(leftToAdd, slot.getMaxStackSize() - slot.getCount());
                    if (add > 0) {
                        slot.grow(add);
                        leftToAdd -= add;
                    }
                }
            }

            for (int i = 0; i < simulated.size() && leftToAdd > 0; i++) {
                ItemStack slot = simulated.get(i);
                if (slot.isEmpty()) {
                    ItemStack placed = original.copy();
                    int add = Math.min(leftToAdd, placed.getMaxStackSize());
                    placed.setCount(add);
                    simulated.set(i, placed);
                    leftToAdd -= add;
                }
            }

            if (leftToAdd > 0) return false;
        }

        return true;
    }

    public void syncState(ServerPlayer player, String message, boolean positive) {
        if (player == null) return;
        PacketHandler.sendToPlayer(new PacketHandler.S2C_EvoWalletState(
                getCashCounts(player.getUUID()),
                hasCard(player.getUUID()),
                message == null ? "" : message,
                positive
        ), player);
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", DATA_VERSION);

            ListTag walletList = new ListTag();
            for (Map.Entry<UUID, WalletData> entry : wallets.entrySet()) {
                WalletData data = entry.getValue();
                if (data.isEmpty()) continue;

                CompoundTag tag = new CompoundTag();
                tag.putString("OwnerUuid", entry.getKey().toString());
                if (!data.card.isEmpty()) {
                    tag.put("Card", data.card.save(new CompoundTag()));
                }

                ListTag cashList = new ListTag();
                for (int denomination : DENOMINATIONS) {
                    int count = data.cashCounts.getOrDefault(denomination, 0);
                    if (count <= 0) continue;
                    CompoundTag cash = new CompoundTag();
                    cash.putInt("Denomination", denomination);
                    cash.putInt("Count", Math.min(CASH_SLOT_LIMIT, count));
                    cashList.add(cash);
                }
                tag.put("Cash", cashList);
                walletList.add(tag);
            }
            root.put("Wallets", walletList);

            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not save EvoWallet data.");
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        wallets.clear();
        if (!file.exists()) return;

        try {
            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null || !root.contains("Wallets")) return;

            ListTag walletList = root.getList("Wallets", 10);
            for (int i = 0; i < walletList.size(); i++) {
                CompoundTag tag = walletList.getCompound(i);
                UUID owner;
                try {
                    owner = UUID.fromString(tag.getString("OwnerUuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }

                WalletData data = new WalletData();
                if (tag.contains("Card")) {
                    ItemStack card = ItemStack.of(tag.getCompound("Card"));
                    if (EvoBankManager.isBankCardItem(card)) {
                        data.card = card;
                    }
                }

                ListTag cashList = tag.getList("Cash", 10);
                for (int c = 0; c < cashList.size(); c++) {
                    CompoundTag cash = cashList.getCompound(c);
                    int denomination = cash.getInt("Denomination");
                    int count = cash.getInt("Count");
                    if (isSupportedDenomination(denomination) && count > 0) {
                        data.cashCounts.put(denomination, Math.min(CASH_SLOT_LIMIT, count));
                    }
                }

                wallets.put(owner, data);
            }
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not load EvoWallet data.");
            e.printStackTrace();
        }
    }

    public static class WalletData {
        private ItemStack card = ItemStack.EMPTY;
        private final Map<Integer, Integer> cashCounts = new HashMap<>();

        private boolean isEmpty() {
            return card.isEmpty() && cashCounts.values().stream().allMatch(count -> count <= 0);
        }

        @Override
        public String toString() {
            return "WalletData{card=" + !card.isEmpty() + ", cash=" + Arrays.toString(cashCounts.entrySet().toArray()) + "}";
        }
    }
}
