package org.evocraft.evocore.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static EconomyManager INSTANCE;

    public static void initialize() { if (INSTANCE == null) INSTANCE = new EconomyManager(); }
    public static EconomyManager get() { if (INSTANCE == null) initialize(); return INSTANCE; }

    // Citește banii direct din memoria unificată a PlayerStatsManager
    public double getBalance(UUID uuid) {
        return PlayerStatsManager.get().getStats(uuid).balance;
    }

    // Setează banii și îi spune lui PlayerStatsManager să salveze instant în MySQL
    public void setBalance(UUID uuid, double amount) {
        PlayerStatsManager.get().getStats(uuid).balance = amount;
        PlayerStatsManager.get().saveToDatabase(uuid);
        PlayerStatsManager.get().syncClient(uuid); // Dă update instant pe ecranul jucătorului!
    }

    public void addBalance(UUID uuid, double amount) {
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public void removeBalance(UUID uuid, double amount) {
        setBalance(uuid, Math.max(0, getBalance(uuid) - amount));
    }

    // Oferă lista tuturor conturilor pentru comenzile de TOP (din EvoHub)
    public Map<UUID, Double> getAllAccounts() {
        Map<UUID, Double> accounts = new HashMap<>();
        for (Map.Entry<UUID, PlayerStatsManager.PlayerStats> entry : PlayerStatsManager.get().getAllStats().entrySet()) {
            accounts.put(entry.getKey(), entry.getValue().balance);
        }
        return accounts;
    }
}