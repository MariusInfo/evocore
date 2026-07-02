package org.evocraft.evocore.client;

public class ClientBalanceData {
    private static double balance = 0.0;

    public static void setBalance(double b) {
        balance = b;
    }

    public static double getBalance() {
        return balance;
    }
}