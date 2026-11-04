package org.evocraft.evocombat.network;

public class CombatClientPacketHandler {
    public static void handleSyncCombat(boolean inCombat, long remainingMs) {
        org.evocraft.evocombat.combat.CombatNotificationHandler.updateCombatStatus(inCombat, remainingMs);
    }
}
