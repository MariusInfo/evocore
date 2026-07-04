package org.evocraft.evocore.data;

import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

public class CrateKeyManager {
    private static CrateKeyManager INSTANCE;

    public static CrateKeyManager get() {
        if (INSTANCE == null) INSTANCE = new CrateKeyManager();
        return INSTANCE;
    }

    public synchronized void addKeys(String username, String crateName, int amount) {
        if (amount <= 0) return;
        String normalizedCrate = normalizeCrateName(crateName);
        if (username == null || username.isBlank() || normalizedCrate.isBlank()) return;

        String query = isMySql()
                ? "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
                : "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON CONFLICT(username, crate_name) DO UPDATE SET amount = amount + excluded.amount";

        try (PreparedStatement ps = connection().prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, normalizedCrate);
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not add crate keys for " + username + " / " + normalizedCrate);
            e.printStackTrace();
        }
    }

    public synchronized void setKeys(String username, String crateName, int amount) {
        String normalizedCrate = normalizeCrateName(crateName);
        if (username == null || username.isBlank() || normalizedCrate.isBlank()) return;
        int safeAmount = Math.max(0, amount);

        String query = isMySql()
                ? "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
                : "INSERT INTO player_keys (username, crate_name, amount) VALUES (?, ?, ?) ON CONFLICT(username, crate_name) DO UPDATE SET amount = excluded.amount";

        try (PreparedStatement ps = connection().prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, normalizedCrate);
            ps.setInt(3, safeAmount);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not set crate keys for " + username + " / " + normalizedCrate);
            e.printStackTrace();
        }
    }

    public synchronized void takeKeys(String username, String crateName, int amount) {
        if (amount <= 0) return;
        String normalizedCrate = normalizeCrateName(crateName);
        if (username == null || username.isBlank() || normalizedCrate.isBlank()) return;

        String query = isMySql()
                ? "UPDATE player_keys SET amount = GREATEST(0, amount - ?) WHERE username = ? AND crate_name = ?"
                : "UPDATE player_keys SET amount = max(0, amount - ?) WHERE username = ? AND crate_name = ?";

        try (PreparedStatement ps = connection().prepareStatement(query)) {
            ps.setInt(1, amount);
            ps.setString(2, username);
            ps.setString(3, normalizedCrate);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not take crate keys from " + username + " / " + normalizedCrate);
            e.printStackTrace();
        }
    }

    public synchronized int getKeys(String username, String crateName) {
        String normalizedCrate = normalizeCrateName(crateName);
        if (username == null || username.isBlank() || normalizedCrate.isBlank()) return 0;

        String query = "SELECT amount FROM player_keys WHERE username = ? AND crate_name = ?";
        try (PreparedStatement ps = connection().prepareStatement(query)) {
            ps.setString(1, username);
            ps.setString(2, normalizedCrate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("amount")) : 0;
            }
        } catch (Exception e) {
            System.err.println("[EvoCore] Could not read crate keys for " + username + " / " + normalizedCrate);
            e.printStackTrace();
            return 0;
        }
    }

    public String normalizeCrateName(String crateName) {
        if (crateName == null) return "";
        return crateName.trim().toLowerCase(Locale.ROOT);
    }

    private Connection connection() {
        return DatabaseManager.get().getConnection();
    }

    private boolean isMySql() {
        return "MYSQL".equalsIgnoreCase(DatabaseManager.get().type);
    }
}
