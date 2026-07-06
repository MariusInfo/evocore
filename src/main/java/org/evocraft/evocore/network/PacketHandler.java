package org.evocraft.evocore.network;

import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.bank.EvoBankAtmMenu;
import org.evocraft.evocore.data.PlayerStatsManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EvoCore.MODID, "main"),
            () -> PROTOCOL_VERSION,
            s -> true,
            s -> true
    );

    private static int packetId = 0;
    private static int nextId() { return packetId++; }

    private static boolean isRegistered = false;

    public static void register() {
        if (isRegistered) return;
        isRegistered = true;

        INSTANCE.registerMessage(nextId(), S2C_BroadcastNotification.class, S2C_BroadcastNotification::toBytes, S2C_BroadcastNotification::new, S2C_BroadcastNotification::handle);
        INSTANCE.registerMessage(nextId(), S2C_SyncBalance.class, S2C_SyncBalance::toBytes, S2C_SyncBalance::new, S2C_SyncBalance::handle);
        INSTANCE.registerMessage(nextId(), C2S_SaveSettings.class, C2S_SaveSettings::toBytes, C2S_SaveSettings::new, C2S_SaveSettings::handle);
        INSTANCE.registerMessage(nextId(), C2S_BlockPlayer.class, C2S_BlockPlayer::toBytes, C2S_BlockPlayer::new, C2S_BlockPlayer::handle);
        INSTANCE.registerMessage(nextId(), S2C_PlayerJoinLeave.class, S2C_PlayerJoinLeave::toBytes, S2C_PlayerJoinLeave::new, S2C_PlayerJoinLeave::handle);
        INSTANCE.registerMessage(nextId(), S2C_SyncCombat.class, S2C_SyncCombat::toBytes, S2C_SyncCombat::new, S2C_SyncCombat::handle);
        INSTANCE.registerMessage(nextId(), S2C_VanishHUD.class, S2C_VanishHUD::toBytes, S2C_VanishHUD::new, S2C_VanishHUD::handle);
        INSTANCE.registerMessage(nextId(), S2C_SyncVanish.class, S2C_SyncVanish::toBytes, S2C_SyncVanish::new, S2C_SyncVanish::handle);
        INSTANCE.registerMessage(nextId(), S2C_SyncStats.class, S2C_SyncStats::toBytes, S2C_SyncStats::new, S2C_SyncStats::handle);
        INSTANCE.registerMessage(nextId(), C2S_HomeActionPacket.class, C2S_HomeActionPacket::toBytes, C2S_HomeActionPacket::new, C2S_HomeActionPacket::handle);
        INSTANCE.registerMessage(nextId(), S2C_SyncHomesPacket.class, S2C_SyncHomesPacket::toBytes, S2C_SyncHomesPacket::new, S2C_SyncHomesPacket::handle);
        INSTANCE.registerMessage(nextId(), C2S_TpaActionPacket.class, C2S_TpaActionPacket::toBytes, C2S_TpaActionPacket::new, C2S_TpaActionPacket::handle);
        INSTANCE.registerMessage(nextId(), S2C_EvoBankAtmState.class, S2C_EvoBankAtmState::toBytes, S2C_EvoBankAtmState::new, S2C_EvoBankAtmState::handle);
        INSTANCE.registerMessage(nextId(), C2S_EvoBankAtmPin.class, C2S_EvoBankAtmPin::toBytes, C2S_EvoBankAtmPin::new, C2S_EvoBankAtmPin::handle);
        INSTANCE.registerMessage(nextId(), C2S_EvoBankAtmWithdraw.class, C2S_EvoBankAtmWithdraw::toBytes, C2S_EvoBankAtmWithdraw::new, C2S_EvoBankAtmWithdraw::handle);
        INSTANCE.registerMessage(nextId(), C2S_EvoBankAtmCustomWithdraw.class, C2S_EvoBankAtmCustomWithdraw::toBytes, C2S_EvoBankAtmCustomWithdraw::new, C2S_EvoBankAtmCustomWithdraw::handle);
        INSTANCE.registerMessage(nextId(), C2S_EvoBankAtmDeposit.class, C2S_EvoBankAtmDeposit::toBytes, C2S_EvoBankAtmDeposit::new, C2S_EvoBankAtmDeposit::handle);
        INSTANCE.registerMessage(nextId(), C2S_EvoBankAtmTransfer.class, C2S_EvoBankAtmTransfer::toBytes, C2S_EvoBankAtmTransfer::new, C2S_EvoBankAtmTransfer::handle);
        INSTANCE.registerMessage(nextId(), S2C_EvoWalletState.class, S2C_EvoWalletState::toBytes, S2C_EvoWalletState::new, S2C_EvoWalletState::handle);

        // --- AM INREGISTRAT PACHETUL PENTRU FLY AICI ---
        INSTANCE.registerMessage(nextId(), S2C_SyncFlyTime.class, S2C_SyncFlyTime::toBytes, S2C_SyncFlyTime::new, S2C_SyncFlyTime::handle);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if(player != null) INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToAll(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    public static class S2C_SyncBalance {
        private final double b; public S2C_SyncBalance(double b){this.b=b;} public S2C_SyncBalance(FriendlyByteBuf buf){this.b=buf.readDouble();} public void toBytes(FriendlyByteBuf buf){buf.writeDouble(b);} public void handle(Supplier<NetworkEvent.Context> s){ s.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->org.evocraft.evocore.network.ClientPacketHandler.handleSyncBalance(b))); s.get().setPacketHandled(true); }
    }

    public static class C2S_SaveSettings {
        public final boolean tpa, trade;
        public C2S_SaveSettings(boolean tpa, boolean trade) { this.tpa = tpa; this.trade = trade; }
        public C2S_SaveSettings(FriendlyByteBuf b) { this.tpa = b.readBoolean(); this.trade = b.readBoolean(); }
        public void toBytes(FriendlyByteBuf b) { b.writeBoolean(tpa); b.writeBoolean(trade); }
        public void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) {
                    PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(p.getUUID());
                    if (stats != null) {
                        stats.allowTPA = tpa;
                        stats.allowTrade = trade;
                        PlayerStatsManager.get().saveToDatabase(p.getUUID());
                    }
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    public static class C2S_BlockPlayer {
        public final String targetName; public final boolean block;
        public C2S_BlockPlayer(String t, boolean b) { this.targetName = t; this.block = b; }
        public C2S_BlockPlayer(FriendlyByteBuf b) { this.targetName = b.readUtf(); this.block = b.readBoolean(); }
        public void toBytes(FriendlyByteBuf b) { b.writeUtf(targetName); b.writeBoolean(block); }
        public void handle(Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p = c.get().getSender();
                if (p != null) {
                    PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(p.getUUID());
                    if (stats != null) {
                        p.getServer().getProfileCache().get(targetName).ifPresentOrElse(profile -> {
                            UUID targetId = profile.getId();
                            if (block && !stats.blockedPlayers.contains(targetId.toString())) {
                                stats.blockedPlayers.add(targetId.toString());
                                p.sendSystemMessage(Component.literal("§aL-ai blocat pe " + targetName + " de la TPA/Trade!"));
                            } else if (!block) {
                                stats.blockedPlayers.remove(targetId.toString());
                                p.sendSystemMessage(Component.literal("§aL-ai deblocat pe " + targetName + " !"));
                            }
                            PlayerStatsManager.get().saveToDatabase(p.getUUID());
                        }, () -> {
                            p.sendSystemMessage(Component.literal("§cJucătorul nu a fost găsit pe server!"));
                        });
                    }
                }
            });
            c.get().setPacketHandled(true);
        }
    }

    public static class S2C_PlayerJoinLeave {
        public final String name; public final UUID uuid; public final boolean isJoin; public S2C_PlayerJoinLeave(String n, UUID u, boolean j) { this.name = n; this.uuid = u; this.isJoin = j; } public S2C_PlayerJoinLeave(FriendlyByteBuf b) { this.name = b.readUtf(); this.uuid = b.readUUID(); this.isJoin = b.readBoolean(); } public void toBytes(FriendlyByteBuf b) { b.writeUtf(name); b.writeUUID(uuid); b.writeBoolean(isJoin); } public void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocore.network.ClientPacketHandler.handlePlayerJoinLeave(name, uuid, isJoin))); c.get().setPacketHandled(true); }
    }
    public static class S2C_SyncCombat {
        public final boolean inCombat; public final long remainingMs; public S2C_SyncCombat(boolean inCombat, long remainingMs) { this.inCombat = inCombat; this.remainingMs = remainingMs; } public S2C_SyncCombat(FriendlyByteBuf b) { this.inCombat = b.readBoolean(); this.remainingMs = b.readLong(); } public void toBytes(FriendlyByteBuf b) { b.writeBoolean(inCombat); b.writeLong(remainingMs); } public void handle(Supplier<NetworkEvent.Context> s) { s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocore.network.ClientPacketHandler.handleSyncCombat(inCombat, remainingMs))); s.get().setPacketHandled(true); }
    }
    public static class S2C_SyncVanish {
        public final List<UUID> vanished; public S2C_SyncVanish(List<UUID> v) { this.vanished = v; } public S2C_SyncVanish(FriendlyByteBuf b) { this.vanished = new ArrayList<>(); int size = b.readInt(); for(int i = 0; i < size; i++) vanished.add(b.readUUID()); } public void toBytes(FriendlyByteBuf b) { b.writeInt(vanished.size()); for(UUID u : vanished) b.writeUUID(u); } public void handle(Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocore.network.ClientPacketHandler.handleSyncVanish(vanished))); c.get().setPacketHandled(true); }
    }
    public static class S2C_VanishHUD {
        public final boolean v; public S2C_VanishHUD(boolean v) { this.v = v; } public S2C_VanishHUD(net.minecraft.network.FriendlyByteBuf b) { this.v = b.readBoolean(); } public void toBytes(net.minecraft.network.FriendlyByteBuf b) { b.writeBoolean(v); } public void handle(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> c) { c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocore.network.ClientPacketHandler.handleVanishHUD(v))); c.get().setPacketHandled(true); }
    }

    public static class S2C_SyncStats {
        public int kills, deaths;
        public long playtimeSec;
        public String rank;

        public S2C_SyncStats(int kills, int deaths, long playtimeSec, String rank) {
            this.kills = kills; this.deaths = deaths; this.playtimeSec = playtimeSec; this.rank = rank;
        }

        public S2C_SyncStats(FriendlyByteBuf buf) {
            this.kills = buf.readInt(); this.deaths = buf.readInt();
            this.playtimeSec = buf.readLong(); this.rank = buf.readUtf();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(kills); buf.writeInt(deaths);
            buf.writeLong(playtimeSec); buf.writeUtf(rank);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                org.evocraft.evocore.client.ClientStatsData.kills = this.kills;
                org.evocraft.evocore.client.ClientStatsData.deaths = this.deaths;
                org.evocraft.evocore.client.ClientStatsData.playtimeSeconds = (int)this.playtimeSec;
                org.evocraft.evocore.client.ClientStatsData.rank = this.rank;
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class S2C_BroadcastNotification {
        public String message;

        public S2C_BroadcastNotification(String message) {
            this.message = message;
        }

        public S2C_BroadcastNotification(net.minecraft.network.FriendlyByteBuf buf) {
            this.message = buf.readUtf();
        }

        public void toBytes(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeUtf(message);
        }

        public boolean handle(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                    org.evocraft.evocore.client.BroadcastOverlay.showMessage(message);
                });
            });
            ctx.get().setPacketHandled(true);
            return true;
        }
    }

    public static class S2C_EvoBankAtmState {
        public final boolean hasCard;
        public final boolean cardValid;
        public final boolean hasPin;
        public final boolean authenticated;
        public final String ownerName;
        public final double balance;
        public final String message;
        public final boolean positive;

        public S2C_EvoBankAtmState(boolean hasCard, boolean cardValid, boolean hasPin, boolean authenticated,
                                   String ownerName, double balance, String message, boolean positive) {
            this.hasCard = hasCard;
            this.cardValid = cardValid;
            this.hasPin = hasPin;
            this.authenticated = authenticated;
            this.ownerName = ownerName;
            this.balance = balance;
            this.message = message;
            this.positive = positive;
        }

        public S2C_EvoBankAtmState(FriendlyByteBuf buf) {
            this.hasCard = buf.readBoolean();
            this.cardValid = buf.readBoolean();
            this.hasPin = buf.readBoolean();
            this.authenticated = buf.readBoolean();
            this.ownerName = buf.readUtf();
            this.balance = buf.readDouble();
            this.message = buf.readUtf();
            this.positive = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(hasCard);
            buf.writeBoolean(cardValid);
            buf.writeBoolean(hasPin);
            buf.writeBoolean(authenticated);
            buf.writeUtf(ownerName == null ? "" : ownerName);
            buf.writeDouble(balance);
            buf.writeUtf(message == null ? "" : message);
            buf.writeBoolean(positive);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    org.evocraft.evocore.network.ClientPacketHandler.handleEvoBankAtmState(
                            hasCard, cardValid, hasPin, authenticated, ownerName, balance, message, positive
                    )));
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_EvoBankAtmPin {
        public final String pin;

        public C2S_EvoBankAtmPin(String pin) {
            this.pin = pin == null ? "" : pin;
        }

        public C2S_EvoBankAtmPin(FriendlyByteBuf buf) {
            this.pin = buf.readUtf(12);
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(pin, 12);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof EvoBankAtmMenu menu) {
                    menu.handlePin(player, pin);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_EvoBankAtmWithdraw {
        public final int amount;
        public final boolean smartStack;

        public C2S_EvoBankAtmWithdraw(int amount) {
            this(amount, false);
        }

        public C2S_EvoBankAtmWithdraw(int amount, boolean smartStack) {
            this.amount = amount;
            this.smartStack = smartStack;
        }

        public C2S_EvoBankAtmWithdraw(FriendlyByteBuf buf) {
            this.amount = buf.readInt();
            this.smartStack = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(amount);
            buf.writeBoolean(smartStack);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof EvoBankAtmMenu menu) {
                    menu.handleWithdraw(player, amount, smartStack);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_EvoBankAtmDeposit {
        public C2S_EvoBankAtmDeposit() {
        }

        public C2S_EvoBankAtmDeposit(FriendlyByteBuf buf) {
        }

        public void toBytes(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof EvoBankAtmMenu menu) {
                    menu.handleDeposit(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_EvoBankAtmCustomWithdraw {
        public final int amount;

        public C2S_EvoBankAtmCustomWithdraw(int amount) {
            this.amount = amount;
        }

        public C2S_EvoBankAtmCustomWithdraw(FriendlyByteBuf buf) {
            this.amount = buf.readInt();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(amount);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof EvoBankAtmMenu menu) {
                    menu.handleCustomWithdraw(player, amount);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_EvoBankAtmTransfer {
        public final String targetName;
        public final double amount;

        public C2S_EvoBankAtmTransfer(String targetName, double amount) {
            this.targetName = targetName == null ? "" : targetName;
            this.amount = amount;
        }

        public C2S_EvoBankAtmTransfer(FriendlyByteBuf buf) {
            this.targetName = buf.readUtf(32);
            this.amount = buf.readDouble();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(targetName, 32);
            buf.writeDouble(amount);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null && player.containerMenu instanceof EvoBankAtmMenu menu) {
                    menu.handleTransfer(player, targetName, amount);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class S2C_EvoWalletState {
        public final int[] cashCounts;
        public final boolean hasCard;
        public final String message;
        public final boolean positive;

        public S2C_EvoWalletState(int[] cashCounts, boolean hasCard, String message, boolean positive) {
            this.cashCounts = cashCounts == null ? new int[0] : Arrays.copyOf(cashCounts, cashCounts.length);
            this.hasCard = hasCard;
            this.message = message == null ? "" : message;
            this.positive = positive;
        }

        public S2C_EvoWalletState(FriendlyByteBuf buf) {
            int size = Math.min(32, Math.max(0, buf.readInt()));
            this.cashCounts = new int[size];
            for (int i = 0; i < size; i++) {
                this.cashCounts[i] = buf.readInt();
            }
            this.hasCard = buf.readBoolean();
            this.message = buf.readUtf(128);
            this.positive = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeInt(cashCounts.length);
            for (int count : cashCounts) {
                buf.writeInt(count);
            }
            buf.writeBoolean(hasCard);
            buf.writeUtf(message, 128);
            buf.writeBoolean(positive);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    org.evocraft.evocore.network.ClientPacketHandler.handleEvoWalletState(cashCounts, hasCard, message, positive)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ==========================================
    // PACHETUL NOU PENTRU FLY
    // ==========================================
    public static class S2C_SyncFlyTime {
        public final boolean isFlying;
        public final int timeRemaining;

        public S2C_SyncFlyTime(boolean isFlying, int timeRemaining) {
            this.isFlying = isFlying;
            this.timeRemaining = timeRemaining;
        }

        public S2C_SyncFlyTime(FriendlyByteBuf buf) {
            this.isFlying = buf.readBoolean();
            this.timeRemaining = buf.readInt();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(isFlying);
            buf.writeInt(timeRemaining);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // Când vine pachetul, salvăm pe client datele pentru interfață!
                    org.evocraft.evocore.network.ClientPacketHandler.handleSyncFlyTime(isFlying, timeRemaining);
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
