package org.evocraft.evocore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2C_SyncHomesPacket {
    private final int currentCount;
    private final int maxCount;
    private final List<String> homes;

    public S2C_SyncHomesPacket(int currentCount, int maxCount, List<String> homes) {
        this.currentCount = currentCount; this.maxCount = maxCount; this.homes = homes;
    }

    public S2C_SyncHomesPacket(FriendlyByteBuf buf) {
        this.currentCount = buf.readInt();
        this.maxCount = buf.readInt();
        this.homes = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) this.homes.add(buf.readUtf());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(currentCount);
        buf.writeInt(maxCount);
        buf.writeInt(homes.size());
        for (String home : homes) {
            // Blindaj suprem anti-crash!
            buf.writeUtf(home != null ? home : "Necunoscut");
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> org.evocraft.evocore.network.ClientPacketHandler.handleSyncHomes(currentCount, maxCount, homes)));
        supplier.get().setPacketHandled(true);
        return true;
    }
}