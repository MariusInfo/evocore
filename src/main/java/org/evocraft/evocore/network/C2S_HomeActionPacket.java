package org.evocraft.evocore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.evocraft.evocore.teleport.HomeManager;
import org.evocraft.evocore.combat.CombatLogManager;

import java.util.function.Supplier;

public class C2S_HomeActionPacket {
    private final String action; // Poate fi: "set", "del", "tp"
    private final String homeName;

    public C2S_HomeActionPacket(String action, String homeName) {
        this.action = action;
        this.homeName = homeName;
    }

    public C2S_HomeActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readUtf();
        this.homeName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(action);
        buf.writeUtf(homeName);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                if (action.equals("set")) {
                    HomeManager.get().setHome(player, homeName);
                } else if (action.equals("del")) {
                    HomeManager.get().delHome(player, homeName);
                } else if (action.equals("tp")) {
                    // Protecție Combat
                    if (CombatLogManager.isInCombat(player)) {
                        player.sendSystemMessage(Component.literal("§c✖ Nu te poți teleporta Acasă cât ești în combat!"));
                        return;
                    }
                    HomeManager.get().teleportHome(player, homeName);
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}