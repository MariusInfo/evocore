package org.evocraft.evocore.data;

import net.minecraft.network.chat.Component;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WarnManager {

    public static void initializeDB() {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (Statement stmt = conn.createStatement()) {
                        // Creăm tabelul pentru warn-uri
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_warnings (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                "uuid VARCHAR(36), " +
                                "reason TEXT, " +
                                "admin_name VARCHAR(32), " +
                                "date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    public static void addWarn(UUID uuid, String reason, String adminName) {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO player_warnings (uuid, reason, admin_name) VALUES (?, ?, ?)")) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, reason);
                        stmt.setString(3, adminName);
                        stmt.executeUpdate();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    public static void removeWarn(int warnId, Runnable onSuccess) {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_warnings WHERE id = ?")) {
                        stmt.setInt(1, warnId);
                        int affected = stmt.executeUpdate();
                        if (affected > 0) onSuccess.run();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    public static List<WarnEntry> getWarns(UUID uuid) {
        List<WarnEntry> warns = new ArrayList<>();
        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_warnings WHERE uuid = ? ORDER BY date DESC")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        warns.add(new WarnEntry(
                                rs.getInt("id"),
                                rs.getString("reason"),
                                rs.getString("admin_name"),
                                rs.getTimestamp("date").toString()
                        ));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return warns;
    }

    public static record WarnEntry(int id, String reason, String admin, String date) {}
}