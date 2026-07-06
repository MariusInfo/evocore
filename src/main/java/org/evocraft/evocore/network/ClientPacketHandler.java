package org.evocraft.evocore.network;

import java.util.List;
import java.util.UUID;

// IMPORTURILE CLARE
import org.evocraft.evocore.client.ClientBalanceData;
import org.evocraft.evocore.client.ClientBankAtmData;
import org.evocraft.evocore.client.ClientWalletData;
import org.evocraft.evocore.client.ClientVanishData;
import org.evocraft.evocore.vanish.VanishOverlay;
import org.evocraft.evocore.client.ClientHomeData; // Importul nou pentru case
import org.evocraft.evocore.client.ClientFlyData; // Importul pentru memoria de Fly!

public class ClientPacketHandler {

    public static void handleSyncBalance(double balance) {
        ClientBalanceData.setBalance(balance);
    }

    public static void handleEvoBankAtmState(boolean hasCard, boolean cardValid, boolean hasPin, boolean authenticated,
                                             String ownerName, double balance, String message, boolean positive) {
        ClientBankAtmData.update(hasCard, cardValid, hasPin, authenticated, ownerName, balance, message, positive);
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof org.evocraft.evocore.client.EvoBankAtmScreen screen) {
            screen.applyStateFromClientData(true);
        }
    }

    public static void handleEvoWalletState(int[] cashCounts, boolean hasCard, String message, boolean positive) {
        ClientWalletData.update(cashCounts, hasCard, message, positive);
    }

    public static void handlePlayerJoinLeave(String name, UUID uuid, boolean isJoin) {
        // Mutat in Hub
    }

    public static void handleSyncCombat(boolean inCombat, long remainingMs) {
        // Asta lipsea! Acum trimite secundele de la server direct pe ecranul tău:
        org.evocraft.evocore.combat.CombatNotificationHandler.updateCombatStatus(inCombat, remainingMs);
    }

    public static void handleVanishHUD(boolean v) {
        VanishOverlay.isVanished = v;
    }

    public static void handleSyncVanish(List<UUID> vanishedList) {
        ClientVanishData.vanishedPlayers = vanishedList;
    }

    // --- METODA PENTRU SINCRONIZAREA CASELOR ---
    public static void handleSyncHomes(int currentCount, int maxCount, List<String> homes) {
        ClientHomeData.currentHomesCount = currentCount;
        ClientHomeData.maxHomesCount = maxCount;
        ClientHomeData.playerHomes = homes;

        // MAGIA CARE DĂ REFRESH LA ECRAN ÎN TIMP REAL!
        if (net.minecraft.client.Minecraft.getInstance().screen != null) {
            net.minecraft.client.Minecraft.getInstance().screen.init(
                    net.minecraft.client.Minecraft.getInstance(),
                    net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                    net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight()
            );
        }
    }

    // ==========================================
    // METODA NOUĂ PENTRU SINCRONIZAREA TIMPULUI DE FLY
    // ==========================================
    public static void handleSyncFlyTime(boolean isFlying, int timeRemaining) {
        ClientFlyData.isFlying = isFlying;
        ClientFlyData.timeRemainingSeconds = timeRemaining;
    }
}
