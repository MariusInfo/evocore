package org.evocraft.evocore.client;

import java.util.Arrays;

public final class ClientWalletData {
    public static int[] cashCounts = new int[9];
    public static boolean hasCard = false;
    public static String message = "";
    public static boolean positive = true;

    private ClientWalletData() {
    }

    public static void update(int[] counts, boolean cardStored, String statusMessage, boolean statusPositive) {
        cashCounts = Arrays.copyOf(counts, 9);
        hasCard = cardStored;
        message = statusMessage == null ? "" : statusMessage;
        positive = statusPositive;
    }
}
