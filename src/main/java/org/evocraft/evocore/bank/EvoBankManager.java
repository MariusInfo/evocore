package org.evocraft.evocore.bank;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.data.EconomyManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
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
    private static final int DATA_VERSION = 2;
    private static final int PIN_RESET_COST = 5000;
    private static final int CARD_REPLACEMENT_COST = 25000;
    private static final Set<Integer> WITHDRAW_AMOUNTS = Set.of(1, 5, 10, 50, 100, 1000, 10000);
    private static final SecureRandom RANDOM = new SecureRandom();

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
        public String pinSalt = "";
        public String pinHash = "";
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
                tag.putString("PinSalt", account.pinSalt == null ? "" : account.pinSalt);
                tag.putString("PinHash", account.pinHash == null ? "" : account.pinHash);
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
                    account.pinSalt = tag.getString("PinSalt");
                    account.pinHash = tag.getString("PinHash");
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
        BankAccount account = getOrCreateAccount(player);

        if (!existed) {
            giveCard(player);
            player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] Your account was created. You received your first bank card for free."));
            player.sendSystemMessage(Component.literal("\u00A77Go to an ATM, insert the card and set your PIN."));
            return;
        }

        double balance = getAccountBalance(account);
        player.sendSystemMessage(Component.literal("\u00A78\u00A7m--------------------------------"));
        player.sendSystemMessage(Component.literal("\u00A7b\u00A7lEvoBank Services"));
        player.sendSystemMessage(Component.literal("\u00A77Balance: \u00A7e" + formatAmount(balance) + " Evo Cash"));
        player.sendSystemMessage(serviceButton("\u00A7a[Reset PIN - 5,000]", "/evobank service pin", "Pay 5,000 Evo Cash and set a new PIN at the ATM."));
        player.sendSystemMessage(serviceButton("\u00A76[Replace Card - 25,000]", "/evobank service card", "Pay 25,000 Evo Cash and invalidate your old card."));
        player.sendSystemMessage(Component.literal("\u00A78\u00A7m--------------------------------"));
    }

    private Component serviceButton(String label, String command, String hover) {
        return Component.literal(label)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    public boolean resetPinForFee(ServerPlayer player) {
        if (!isNearBanker(player)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You must be near the banker NPC for this service."));
            return false;
        }

        BankAccount account = accounts.get(player.getUUID());
        if (account == null) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You do not have a bank account yet."));
            return false;
        }

        if (getAccountBalance(account) < PIN_RESET_COST) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] PIN reset costs \u00A7e5,000 Evo Cash\u00A7c."));
            return false;
        }

        EconomyManager.get().removeBalance(account.ownerUuid, PIN_RESET_COST);
        account.pinSalt = "";
        account.pinHash = "";
        save();
        player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] PIN reset paid. Insert your card at an ATM and set a new PIN."));
        return true;
    }

    public boolean replaceCardForFee(ServerPlayer player) {
        if (!isNearBanker(player)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You must be near the banker NPC for this service."));
            return false;
        }

        BankAccount account = accounts.get(player.getUUID());
        if (account == null) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You do not have a bank account yet."));
            return false;
        }

        if (getAccountBalance(account) < CARD_REPLACEMENT_COST) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Card replacement costs \u00A7e25,000 Evo Cash\u00A7c."));
            return false;
        }

        EconomyManager.get().removeBalance(account.ownerUuid, CARD_REPLACEMENT_COST);
        account.cardId = UUID.randomUUID().toString();
        save();
        giveCard(player);
        player.sendSystemMessage(Component.literal("\u00A7a[EvoBank] New card issued. The old card is no longer valid."));
        return true;
    }

    public boolean isNearBanker(ServerPlayer player) {
        AABB area = new AABB(player.blockPosition()).inflate(5.0D);
        return !player.level().getEntitiesOfClass(Villager.class, area,
                villager -> villager.getTags().contains(BANKER_TAG)).isEmpty();
    }

    public ItemStack createBankCard(ServerPlayer player) {
        BankAccount account = getOrCreateAccount(player);
        ItemStack card = new ItemStack(EvoCore.EVOBANK_CARD.get());
        card.setHoverName(Component.literal("\u00A7b\u00A7lEvoBank Card \u00A77- \u00A7f" + account.ownerName));

        CompoundTag tag = card.getOrCreateTag();
        tag.putBoolean(CARD_TAG, true);
        tag.putString(CARD_OWNER_TAG, account.ownerUuid.toString());
        tag.putString(CARD_OWNER_NAME_TAG, account.ownerName);
        tag.putString(CARD_ID_TAG, account.cardId);
        return card;
    }

    public void giveCard(ServerPlayer player) {
        giveOrDrop(player, createBankCard(player));
    }

    public boolean hasValidBankCard(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (getAccountByCard(player.getInventory().getItem(i)) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidBankCard(ServerPlayer player, ItemStack stack) {
        return getAccountByCard(stack) != null;
    }

    public static boolean isBankCardItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(CARD_TAG) && !tag.getString(CARD_ID_TAG).isBlank();
    }

    public synchronized BankAccount getAccountByCard(ItemStack stack) {
        if (!isBankCardItem(stack)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        String cardId = tag.getString(CARD_ID_TAG);
        String owner = tag.getString(CARD_OWNER_TAG);
        for (BankAccount account : accounts.values()) {
            if (account.cardId != null && account.cardId.equals(cardId)) {
                if (owner.isBlank() || account.ownerUuid.toString().equals(owner)) {
                    return account;
                }
            }
        }
        return null;
    }

    public String getCardId(ItemStack stack) {
        if (!isBankCardItem(stack) || stack.getTag() == null) return "";
        return stack.getTag().getString(CARD_ID_TAG);
    }

    public boolean hasPin(BankAccount account) {
        return account != null && account.pinSalt != null && !account.pinSalt.isBlank()
                && account.pinHash != null && !account.pinHash.isBlank();
    }

    public boolean setPin(BankAccount account, String pin) {
        if (account == null || !isValidPin(pin)) return false;
        account.pinSalt = createSalt();
        account.pinHash = hashPin(account.pinSalt, pin);
        save();
        return true;
    }

    public boolean verifyPin(BankAccount account, String pin) {
        if (account == null || !hasPin(account) || !isValidPin(pin)) return false;
        return account.pinHash.equals(hashPin(account.pinSalt, pin));
    }

    public boolean isValidPin(String pin) {
        return pin != null && pin.matches("\\d{4,6}");
    }

    public ItemStack createCashItem(int amount) {
        int safeAmount = Math.max(1, amount);
        ItemStack moneyItem = new ItemStack(getCashItem(safeAmount));
        moneyItem.setHoverName(Component.literal("\u00A76\u00A7lEvo Cash: \u00A7e" + formatAmount(safeAmount)));
        CompoundTag tag = moneyItem.getOrCreateTag();
        tag.putBoolean(CASH_TAG, true);
        tag.putInt("EvoMoney", safeAmount);
        return moneyItem;
    }

    public static boolean isCashItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (getCashAmountFromItem(stack) > 0) return true;
        if (!stack.hasTag()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("EvoMoney") && tag.getInt("EvoMoney") > 0;
    }

    public static double getCashValue(ItemStack stack) {
        if (!isCashItem(stack)) return 0.0D;
        int amount = 0;
        if (stack.getTag() != null && stack.getTag().contains("EvoMoney")) {
            amount = stack.getTag().getInt("EvoMoney");
        }
        if (amount <= 0) amount = getCashAmountFromItem(stack);
        if (amount <= 0) return 0.0D;
        return amount * (double) stack.getCount();
    }

    public boolean withdrawFromAccount(ServerPlayer actor, BankAccount account, int amount) {
        if (actor == null || account == null || !WITHDRAW_AMOUNTS.contains(amount)) return false;
        if (getAccountBalance(account) + 0.0001D < amount) return false;

        EconomyManager.get().removeBalance(account.ownerUuid, amount);
        giveOrDrop(actor, createCashItem(amount));
        return true;
    }

    public boolean depositToAccount(BankAccount account, double amount) {
        if (account == null || !Double.isFinite(amount) || amount <= 0.0D) return false;
        EconomyManager.get().addBalance(account.ownerUuid, amount);
        return true;
    }

    public boolean transfer(BankAccount from, BankAccount to, double amount) {
        if (from == null || to == null || from.ownerUuid.equals(to.ownerUuid)) return false;
        if (!Double.isFinite(amount) || amount <= 0.0D) return false;
        if (getAccountBalance(from) + 0.0001D < amount) return false;

        EconomyManager.get().removeBalance(from.ownerUuid, amount);
        EconomyManager.get().addBalance(to.ownerUuid, amount);
        return true;
    }

    public synchronized BankAccount findAccountByName(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (BankAccount account : accounts.values()) {
            if (account.ownerName != null && account.ownerName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return account;
            }
        }
        return null;
    }

    public double getAccountBalance(BankAccount account) {
        return account == null ? 0.0D : EconomyManager.get().getBalance(account.ownerUuid);
    }

    public boolean withdraw(ServerPlayer player, int amount) {
        player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] Use an ATM, insert the card and enter the PIN."));
        return false;
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

    private static Item getCashItem(int amount) {
        return switch (amount) {
            case 1 -> EvoCore.EVOCASH_1.get();
            case 5 -> EvoCore.EVOCASH_5.get();
            case 10 -> EvoCore.EVOCASH_10.get();
            case 50 -> EvoCore.EVOCASH_50.get();
            case 100 -> EvoCore.EVOCASH_100.get();
            case 500 -> EvoCore.EVOCASH_500.get();
            case 1000 -> EvoCore.EVOCASH_1000.get();
            case 5000 -> EvoCore.EVOCASH_5000.get();
            case 10000 -> EvoCore.EVOCASH_10000.get();
            default -> EvoCore.EVOCASH_1.get();
        };
    }

    private static int getCashAmountFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.is(EvoCore.EVOCASH_1.get())) return 1;
        if (stack.is(EvoCore.EVOCASH_5.get())) return 5;
        if (stack.is(EvoCore.EVOCASH_10.get())) return 10;
        if (stack.is(EvoCore.EVOCASH_50.get())) return 50;
        if (stack.is(EvoCore.EVOCASH_100.get())) return 100;
        if (stack.is(EvoCore.EVOCASH_500.get())) return 500;
        if (stack.is(EvoCore.EVOCASH_1000.get())) return 1000;
        if (stack.is(EvoCore.EVOCASH_5000.get())) return 5000;
        if (stack.is(EvoCore.EVOCASH_10000.get())) return 10000;
        return 0;
    }

    public static String formatAmount(double amount) {
        return String.format(Locale.US, "%,.0f", amount);
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static String createSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hashPin(String salt, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash EvoBank PIN", e);
        }
    }

    private static String positionKey(Level level, BlockPos pos) {
        return positionKey(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static String positionKey(String dimension, int x, int y, int z) {
        return dimension + ";" + x + ";" + y + ";" + z;
    }
}
