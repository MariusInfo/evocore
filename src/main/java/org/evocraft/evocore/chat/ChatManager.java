package org.evocraft.evocore.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "evocore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChatManager {

    // Aici ținem timpul rămas în secunde pentru jucătorii ONLINE
    public static final Map<UUID, Integer> activeMutes = new ConcurrentHashMap<>();

    // Variabila pentru Global Mute (Când e event)
    public static boolean isGlobalChatMuted = false;

    // ==========================================
    // 1. INIȚIALIZARE BAZĂ DE DATE
    // ==========================================
    public static void initializeDB() {
        new Thread(() -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_mutes (" +
                                "uuid VARCHAR(36) PRIMARY KEY, " +
                                "time_left INT DEFAULT 0)");
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }

    // ==========================================
    // 2. EVENIMENTE DE JOIN ȘI LEAVE (Optimizare RAM)
    // ==========================================
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            new Thread(() -> {
                synchronized (DatabaseManager.get()) {
                    try {
                        Connection conn = DatabaseManager.get().getConnection();
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT time_left FROM player_mutes WHERE uuid = ?")) {
                            stmt.setString(1, player.getUUID().toString());
                            ResultSet rs = stmt.executeQuery();
                            if (rs.next()) {
                                int timeLeft = rs.getInt("time_left");
                                if (timeLeft > 0) activeMutes.put(player.getUUID(), timeLeft);
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }).start();
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            int timeLeft = activeMutes.getOrDefault(uuid, 0);

            // Salvăm timpul în Baza de Date abia CÂND IESE de pe server
            new Thread(() -> {
                synchronized (DatabaseManager.get()) {
                    try {
                        Connection conn = DatabaseManager.get().getConnection();
                        try (PreparedStatement stmt = conn.prepareStatement(
                                "INSERT INTO player_mutes (uuid, time_left) VALUES (?, ?) ON DUPLICATE KEY UPDATE time_left = ?")) {
                            stmt.setString(1, uuid.toString());
                            stmt.setInt(2, timeLeft);
                            stmt.setInt(3, timeLeft);
                            stmt.executeUpdate();
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }).start();

            // Ștergem din RAM ca să nu ocupe memorie aiurea
            activeMutes.remove(uuid);
        }
    }

    // ==========================================
    // 3. SCURGEREA TIMPULUI DOAR CÂND E ONLINE
    // ==========================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isServer() && event.phase == TickEvent.Phase.END) {
            if (event.player.tickCount % 20 == 0) { // Se execută o dată pe secundă
                ServerPlayer player = (ServerPlayer) event.player;
                UUID uuid = player.getUUID();

                if (activeMutes.containsKey(uuid)) {
                    int left = activeMutes.get(uuid) - 1;
                    if (left <= 0) {
                        activeMutes.remove(uuid);
                        player.sendSystemMessage(Component.literal("§a✔ Mute-ul tău a expirat! Poți folosi chat-ul din nou."));
                    } else {
                        activeMutes.put(uuid, left);
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. BLOCAREA EFECTIVĂ A CHAT-ULUI
    // ==========================================
    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID uuid = player.getUUID();

        // Verificăm dacă e GLOBAL MUTE (Bypass pentru grade)
        if (isGlobalChatMuted && !hasPermission(player, "evocore.mutechat.bypass")) {
            player.sendSystemMessage(Component.literal("§c✖ Chat-ul global este OPRIT momentan pentru toată lumea!"));
            event.setCanceled(true);
            return;
        }

        // Verificăm dacă jucătorul are MUTE PERSONAL
        if (activeMutes.containsKey(uuid)) {
            int timeLeft = activeMutes.get(uuid);
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;

            player.sendSystemMessage(Component.literal("§c✖ Ai primit MUTE pe chat!"));
            player.sendSystemMessage(Component.literal("§c✖ Mai ai de așteptat: §e" + minutes + "m și " + seconds + "s§c (Trebuie să fii online ca timpul să scadă)."));
            event.setCanceled(true);
        }
    }

    // ==========================================
    // HELPER PENTRU LUCKPERMS
    // ==========================================
    public static boolean hasPermission(ServerPlayer player, String permission) {
        // Dacă e Operator (OP), are acces la tot din oficiu
        if (player.hasPermissions(2)) return true;

        // Verificăm permisiunea prin LuckPerms API
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            }
        } catch (Exception e) {}

        return false;
    }
}