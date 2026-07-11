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
    public static final int DATA_VERSION = 2;
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

    public static String formatCount(long count) {
        long safe = Math.max(0L, count);
        if (safe < 1_000L) return Long.toString(safe);

        double value = safe;
        String[] suffixes = {"K", "M", "B", "T", "Q"};
        int suffix = -1;
        while (value >= 1_000.0D && suffix + 1 < suffixes.length) {
            value /= 1_000.0D;
            suffix++;
        }

        if (value >= 100.0D) return String.format("%.0f%s", value, suffixes[suffix]);
        if (value >= 10.0D) return String.format("%.1f%s", value, suffixes[suffix]);
        return String.format("%.2f%s", value, suffixes[suffix]);
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
        ItemStack stored = EvoBankManager.get().normalizeBankCard(stack);
        if (stored.isEmpty()) return false;
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

    public synchronized long getCashCount(UUID owner, int denomination) {
        if (!isSupportedDenomination(denomination)) return 0L;
        return getOrCreate(owner).cashCounts.getOrDefault(denomination, 0L);
    }

    public synchronized long[] getCashCounts(UUID owner) {
        WalletData data = getOrCreate(owner);
        long[] counts = new long[DENOMINATIONS.length];
        for (int i = 0; i < DENOMINATIONS.length; i++) {
            counts[i] = data.cashCounts.getOrDefault(DENOMINATIONS[i], 0L);
        }
        return counts;
    }

    public synchronized int addCash(UUID owner, int denomination, int requestedCount) {
        if (!isSupportedDenomination(denomination) || requestedCount <= 0) return 0;
        WalletData data = getOrCreate(owner);
        long current = data.cashCounts.getOrDefault(denomination, 0L);
        long acceptedLong = Math.min((long) requestedCount, Long.MAX_VALUE - current);
        int accepted = acceptedLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) acceptedLong;
        if (accepted <= 0) return 0;
        data.cashCounts.put(denomination, current + accepted);
        save();
        return accepted;
    }

    public synchronized int removeCash(UUID owner, int denomination, int requestedCount) {
        if (!isSupportedDenomination(denomination) || requestedCount <= 0) return 0;
        WalletData data = getOrCreate(owner);
        long current = data.cashCounts.getOrDefault(denomination, 0L);
        int removed = (int) Math.min((long) requestedCount, current);
        if (removed <= 0) return 0;
        long remaining = current - removed;
        if (remaining > 0) {
            data.cashCounts.put(denomination, remaining);
        } else {
            data.cashCounts.remove(denomination);
        }
        save();
        return removed;
    }

    public ItemStack createDisplayCashStack(int denomination, long count) {
        if (count <= 0 || !isSupportedDenomination(denomination)) return ItemStack.EMPTY;
        ItemStack stack = EvoBankManager.get().createCashItem(denomination);
        stack.setCount(1);
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
                    long count = data.cashCounts.getOrDefault(denomination, 0L);
                    if (count <= 0) continue;
                    CompoundTag cash = new CompoundTag();
                    cash.putInt("Denomination", denomination);
                    cash.putLong("Count", count);
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
                        ItemStack normalized = EvoBankManager.get().normalizeBankCard(card);
                        if (!normalized.isEmpty()) {
                            data.card = normalized;
                        }
                    }
                }

                ListTag cashList = tag.getList("Cash", 10);
                for (int c = 0; c < cashList.size(); c++) {
                    CompoundTag cash = cashList.getCompound(c);
                    int denomination = cash.getInt("Denomination");
                    long count = cash.getLong("Count");
                    if (isSupportedDenomination(denomination) && count > 0) {
                        data.cashCounts.put(denomination, count);
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
        private final Map<Integer, Long> cashCounts = new HashMap<>();

        private boolean isEmpty() {
            return card.isEmpty() && cashCounts.values().stream().allMatch(count -> count <= 0);
        }

        @Override
        public String toString() {
            return "WalletData{card=" + !card.isEmpty() + ", cash=" + Arrays.toString(cashCounts.entrySet().toArray()) + "}";
        }
    }
}
