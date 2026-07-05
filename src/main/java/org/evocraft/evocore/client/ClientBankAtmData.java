package org.evocraft.evocore.client;

public class ClientBankAtmData {
    public static boolean hasCard = false;
    public static boolean cardValid = false;
    public static boolean hasPin = false;
    public static boolean authenticated = false;
    public static String ownerName = "";
    public static double balance = 0.0D;
    public static String message = "";
    public static boolean positive = true;

    public static void update(boolean hasCard, boolean cardValid, boolean hasPin, boolean authenticated,
                              String ownerName, double balance, String message, boolean positive) {
        ClientBankAtmData.hasCard = hasCard;
        ClientBankAtmData.cardValid = cardValid;
        ClientBankAtmData.hasPin = hasPin;
        ClientBankAtmData.authenticated = authenticated;
        ClientBankAtmData.ownerName = ownerName == null ? "" : ownerName;
        ClientBankAtmData.balance = balance;
        ClientBankAtmData.message = message == null ? "" : message;
        ClientBankAtmData.positive = positive;
    }
}
