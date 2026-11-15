package org.evocraft.evocore.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private static EconomyManager INSTANCE;

    public static synchronized void initialize() { if (INSTANCE == null) INSTANCE = new EconomyManager(); }
    public static synchronized EconomyManager get() { if (INSTANCE == null) initialize(); return INSTANCE; }

    // Citește banii direct din memoria unificată a PlayerStatsManager
    public double getBalance(UUID uuid) {
        if (uuid == null) return 0.0;
        PlayerStatsManager.PlayerStats s = PlayerStatsManager.get().getStats(uuid);
        synchronized (s) {
            return (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0;
        }
    }

    // Setează banii și îi spune lui PlayerStatsManager să salveze instant în MySQL
    public void setBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount)) return;
        double safe = Math.max(0.0, amount);
        PlayerStatsManager.PlayerStats s = PlayerStatsManager.get().getStats(uuid);
        synchronized (s) {
            s.balance = safe;
        }
        PlayerStatsManager.get().saveToDatabase(uuid);
        PlayerStatsManager.get().syncClient(uuid); // Dă update instant pe ecranul jucătorului!
    }

    public void addBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount <= 0.0) return;
        PlayerStatsManager.PlayerStats s = PlayerStatsManager.get().getStats(uuid);
        synchronized (s) {
            double current = (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0;
            double next = current + amount;
            if (!Double.isFinite(next)) return;
            s.balance = next;
        }
        PlayerStatsManager.get().saveToDatabase(uuid);
        PlayerStatsManager.get().syncClient(uuid);
    }

    public void removeBalance(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount <= 0.0) return;
        PlayerStatsManager.PlayerStats s = PlayerStatsManager.get().getStats(uuid);
        synchronized (s) {
            double current = (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0;
            s.balance = Math.max(0.0, current - amount);
        }
        PlayerStatsManager.get().saveToDatabase(uuid);
        PlayerStatsManager.get().syncClient(uuid);
    }

    public boolean tryWithdraw(UUID uuid, double amount) {
        if (uuid == null || !Double.isFinite(amount) || amount < 0.0) return false;
        if (amount == 0.0) return true;
        PlayerStatsManager.PlayerStats s = PlayerStatsManager.get().getStats(uuid);
        synchronized (s) {
            double current = (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0;
            if (current < amount) return false;
            s.balance = Math.max(0.0, current - amount);
        }
        PlayerStatsManager.get().saveToDatabase(uuid);
        PlayerStatsManager.get().syncClient(uuid);
        return true;
    }

    // Oferă lista tuturor conturilor pentru comenzile de TOP (din EvoHub)
    public Map<UUID, Double> getAllAccounts() {
        Map<UUID, Double> accounts = new HashMap<>();
        for (Map.Entry<UUID, PlayerStatsManager.PlayerStats> entry : PlayerStatsManager.get().getAllStats().entrySet()) {
            PlayerStatsManager.PlayerStats s = entry.getValue();
            synchronized (s) {
                accounts.put(entry.getKey(), (Double.isFinite(s.balance) && s.balance >= 0.0) ? s.balance : 0.0);
            }
        }
        return accounts;
    }
}