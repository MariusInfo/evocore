package org.evocraft.evocore.network;

import java.util.List;
import java.util.UUID;

import org.evocraft.evocore.client.ClientBalanceData;
import org.evocraft.evocore.client.ClientVanishData;
import org.evocraft.evocore.client.ClientFlyData;

public class ClientPacketHandler {

    public static void handleSyncBalance(double balance) {
        ClientBalanceData.setBalance(balance);
    }

    public static void handlePlayerJoinLeave(String name, UUID uuid, boolean isJoin) {
        try {
            Class<?> handler = Class.forName("org.evocraft.evohub.client.JoinLeaveNotificationHandler");
            handler.getMethod("addNotification", String.class, UUID.class, boolean.class)
                    .invoke(null, name, uuid, isJoin);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }
    public static void handleVanishHUD(boolean v) {
        try {
            Class<?> overlay = Class.forName("org.evocraft.evoessentials.vanish.VanishOverlay");
            overlay.getField("isVanished").setBoolean(null, v);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    public static void handleSyncVanish(List<UUID> vanishedList) {
        ClientVanishData.vanishedPlayers = vanishedList == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(vanishedList);
    }

    public static void handleSyncFlyTime(boolean isFlying, int timeRemaining) {
        ClientFlyData.isFlying = isFlying;
        ClientFlyData.timeRemainingSeconds = timeRemaining;
    }
}

