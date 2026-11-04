package org.evocraft.evocombat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class EvoCombatPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("evocombat:main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int nextId() { return packetId++; }

    public static void register() {
        INSTANCE.registerMessage(nextId(), S2C_SyncCombat.class, S2C_SyncCombat::toBytes, S2C_SyncCombat::new, S2C_SyncCombat::handle);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static class S2C_SyncCombat {
        public final boolean inCombat;
        public final long remainingMs;
        public S2C_SyncCombat(boolean inCombat, long remainingMs) { this.inCombat = inCombat; this.remainingMs = remainingMs; }
        public S2C_SyncCombat(FriendlyByteBuf b) { this.inCombat = b.readBoolean(); this.remainingMs = b.readLong(); }
        public void toBytes(FriendlyByteBuf b) { b.writeBoolean(inCombat); b.writeLong(remainingMs); }
        public void handle(Supplier<NetworkEvent.Context> s) {
            s.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocombat.network.CombatClientPacketHandler.handleSyncCombat(inCombat, remainingMs)));
            s.get().setPacketHandled(true);
        }
    }
}
