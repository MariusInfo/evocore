package org.evocraft.evocore.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.data.EconomyManager;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EvoBankManager {
    public static final String BANKER_TAG = "EvoBankNPC";

    private static final String CARD_TAG = "EvoBankCard";
    private static final String CARD_OWNER_TAG = "EvoBankOwner";
    private static final String CARD_OWNER_NAME_TAG = "EvoBankOwnerName";
    private static final String CARD_ID_TAG = "EvoBankCardId";
    private static final String CASH_TAG = "EvoCash";
    private static final int CARD_MODEL_DATA = 730001;
    private static final int DATA_VERSION = 1;
    private static final Set<Integer> WITHDRAW_AMOUNTS = Set.of(1, 5, 10, 50, 100, 1000, 10000);

    private static EvoBankManager INSTANCE;

    public static void initialize() {
        if (INSTANCE == null) INSTANCE = new EvoBankManager();
    }

    public static EvoBankManager get() {
        if (INSTANCE == null) initialize();
        return INSTANCE;
    }

    public static class BankAccount {
        public UUID ownerUuid;
        public String ownerName;
        public String cardId;
    }

    private static class AtmPosition {
        String dimension;
        int x;
        int y;
        int z;

        String key() {
            return positionKey(dimension, x, y, z);
        }
    }

    private final Map<UUID, BankAccount> accounts = new HashMap<>();
    private final Map<String, AtmPosition> atms = new HashMap<>();
    private final File file = FMLPaths.CONFIGDIR.get().resolve("evocore/evobank.nbt").toFile();

    private EvoBankManager() {
        load();
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", DATA_VERSION);

            ListTag accountList = new ListTag();
            for (BankAccount account : accounts.values()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("OwnerUuid", account.ownerUuid.toString());
                tag.putString("OwnerName", account.ownerName == null ? "" : account.ownerName);
                tag.putString("CardId", account.cardId == null ? "" : account.cardId);
                accountList.add(tag);
            }
            root.put("Accounts", accountList);

            ListTag atmList = new ListTag();
            for (AtmPosition atm : atms.values()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Dimension", atm.dimension);
                tag.putInt("X", atm.x);
                tag.putInt("Y", atm.y);
                tag.putInt("Z", atm.z);
                atmList.add(tag);
            }
            root.put("Atms", atmList);

            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not save EvoBank data.");
            e.printStackTrace();
        }
    }

    private synchronized void load() {
        accounts.clear();
        atms.clear();
        if (!file.exists()) return;

        try {
            CompoundTag root = NbtIo.readCompressed(file);
            if (root == null) return;

            ListTag accountList = root.getList("Accounts", 10);
            for (int i = 0; i < accountList.size(); i++) {
                CompoundTag tag = accountList.getCompound(i);
                try {
                    UUID uuid = UUID.fromString(tag.getString("OwnerUuid"));
                    BankAccount account = new BankAccount();
                    account.ownerUuid = uuid;
                    account.ownerName = tag.getString("OwnerName");
                    account.cardId = tag.getString("CardId");
                    if (account.cardId == null || account.cardId.isBlank()) {
                        account.cardId = UUID.randomUUID().toString();
                    }
                    accounts.put(uuid, account);
                } catch (IllegalArgumentException ignored) {
                    System.err.println("[EvoCore] Skipped one invalid EvoBank account entry.");
                }
            }

            ListTag atmList = root.getList("Atms", 10);
            for (int i = 0; i < atmList.size(); i++) {
                CompoundTag tag = atmList.getCompound(i);
                AtmPosition atm = new AtmPosition();
                atm.dimension = tag.getString("Dimension");
                atm.x = tag.getInt("X");
                atm.y = tag.getInt("Y");
                atm.z = tag.getInt("Z");
                if (atm.dimension != null && !atm.dimension.isBlank()) {
                    atms.put(atm.key(), atm);
                }
            }

            System.out.println("[EvoCore] Loaded " + accounts.size() + " EvoBank account(s) and " + atms.size() + " ATM(s).");
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not load EvoBank data.");
            e.printStackTrace();
        }
    }

    public synchronized BankAccount getOrCreateAccount(ServerPlayer player) {
        BankAccount account = accounts.get(player.getUUID());
        if (account == null) {
            account = new BankAccount();
            account.ownerUuid = player.getUUID();
            account.ownerName = player.getGameProfile().getName();
            account.cardId = UUID.randomUUID().toString();
            accounts.put(account.ownerUuid, account);
            save();
            return account;
        }

        String currentName = player.getGameProfile().getName();
        if (account.ownerName == null || !account.ownerName.equals(currentName)) {
            account.ownerName = currentName;
            save();
        }
        return account;
    }

    public synchronized boolean hasAccount(UUID uuid) {
        return accounts.containsKey(uuid);
    }

    public void handleBankerInteraction(ServerPlayer player) {
        boolean existed = hasAccount(player.getUUID());
        getOrCreateAccount(player);

        if (!existed) {
            giveCard(player);
            player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Your bank account was created. You received your personal card."));
            return;
        }

        if (!hasValidBankCard(player)) {
            giveCard(player);
            player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Your replacement bank card was issued."));
            return;
        }

        double balance = EconomyManager.get().getBalance(player.getUUID());
        player.sendSystemMessage(Component.literal("\u00A7b[EvoBank] Account active. Balance: \u00A7e" + formatAmount(balance) + " Evo Cash\u00A7b. Use your card at an ATM."));
    }

    public ItemStack createBankCard(ServerPlayer player) {
        BankAccount account = getOrCreateAccount(player);
        ItemStack card = new ItemStack(Items.PAPER);
        card.setHoverName(Component.literal("\u00A7b\u00A7lEvoBank Card \u00A77- \u00A7f" + account.ownerName));

        CompoundTag tag = card.getOrCreateTag();
        tag.putBoolean(CARD_TAG, true);
        tag.putString(CARD_OWNER_TAG, account.ownerUuid.toString());
        tag.putString(CARD_OWNER_NAME_TAG, account.ownerName);
        tag.putString(CARD_ID_TAG, account.cardId);
        tag.putInt("CustomModelData", CARD_MODEL_DATA);
        return card;
    }

    public void giveCard(ServerPlayer player) {
        giveOrDrop(player, createBankCard(player));
    }

    public boolean hasValidBankCard(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isValidBankCard(player, player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidBankCard(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(CARD_TAG)) return false;

        BankAccount account = accounts.get(player.getUUID());
        if (account == null) return false;

        return player.getUUID().toString().equals(tag.getString(CARD_OWNER_TAG))
                && account.cardId.equals(tag.getString(CARD_ID_TAG));
    }

    public ItemStack createCashItem(int amount) {
        int safeAmount = Math.max(1, amount);
        ItemStack moneyItem = new ItemStack(Items.PAPER);
        moneyItem.setHoverName(Component.literal("\u00A76\u00A7lEvo Cash: \u00A7e" + formatAmount(safeAmount)));
        CompoundTag tag = moneyItem.getOrCreateTag();
        tag.putBoolean(CASH_TAG, true);
        tag.putInt("EvoMoney", safeAmount);
        applyCashModel(moneyItem, safeAmount);
        return moneyItem;
    }

    public static void applyCashModel(ItemStack stack, int amount) {
        int modelData = getCashModelData(amount);
        if (modelData > 0) {
            stack.getOrCreateTag().putInt("CustomModelData", modelData);
        }
    }

    public static int getCashModelData(int amount) {
        return switch (amount) {
            case 1 -> 730101;
            case 5 -> 730105;
            case 10 -> 730110;
            case 50 -> 730150;
            case 100 -> 730200;
            case 500 -> 730600;
            case 1000 -> 731100;
            case 5000 -> 735100;
            case 10000 -> 740100;
            default -> 0;
        };
    }

    public boolean withdraw(ServerPlayer player, int amount) {
        if (!WITHDRAW_AMOUNTS.contains(amount)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] This ATM cannot withdraw that amount."));
            return false;
        }

        if (!hasValidBankCard(player)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You need your personal EvoBank card to use this ATM."));
            return false;
        }

        double balance = EconomyManager.get().getBalance(player.getUUID());
        if (balance + 0.0001D < amount) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Not enough funds. Balance: \u00A7e" + formatAmount(balance) + " Evo Cash"));
            return false;
        }

        EconomyManager.get().removeBalance(player.getUUID(), amount);
        giveOrDrop(player, createCashItem(amount));
        player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Withdrawn \u00A7e" + formatAmount(amount) + " Evo Cash\u00A7a."));
        return true;
    }

    public boolean addAtm(Level level, BlockPos pos) {
        AtmPosition atm = new AtmPosition();
        atm.dimension = level.dimension().location().toString();
        atm.x = pos.getX();
        atm.y = pos.getY();
        atm.z = pos.getZ();
        boolean changed = atms.put(atm.key(), atm) == null;
        save();
        return changed;
    }

    public boolean removeAtm(Level level, BlockPos pos) {
        boolean removed = atms.remove(positionKey(level, pos)) != null;
        if (removed) save();
        return removed;
    }

    public boolean isAtm(Level level, BlockPos pos) {
        return atms.containsKey(positionKey(level, pos));
    }

    public int getAtmCount() {
        return atms.size();
    }

    public int getAccountCount() {
        return accounts.size();
    }

    public boolean isAllowedWithdrawAmount(int amount) {
        return WITHDRAW_AMOUNTS.contains(amount);
    }

    public static String formatAmount(double amount) {
        return String.format(Locale.US, "%,.0f", amount);
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static String positionKey(Level level, BlockPos pos) {
        return positionKey(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static String positionKey(String dimension, int x, int y, int z) {
        return dimension + ";" + x + ";" + y + ";" + z;
    }
}
