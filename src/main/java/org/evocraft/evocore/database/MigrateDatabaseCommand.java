package org.evocraft.evocore.database;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class MigrateDatabaseCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evocore")
                .then(Commands.literal("migratedb")
                        .requires(src -> src.hasPermission(3))
                        .executes(MigrateDatabaseCommand::executeMigration)
                )
        );
    }

    private static boolean isAllowedTable(String name) {
        if (name == null || !name.matches("^[A-Za-z0-9_]+$")) return false;
        return name.startsWith("player_")
                || name.startsWith("claim_")
                || name.startsWith("plot_claims")
                || name.startsWith("evoprotection_")
                || "daily_quests".equals(name);
    }

    private static void sendFeedback(CommandSourceStack source, Component msg, boolean error) {
        var server = source.getServer();
        if (server != null) {
            server.execute(() -> {
                if (error) source.sendFailure(msg);
                else source.sendSuccess(() -> msg, true);
            });
        }
    }

    private static int executeMigration(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DatabaseManager db = DatabaseManager.get();
        if (db.isSqlite()) {
            source.sendFailure(Component.literal("§cCurrent database type is SQLITE. Change type to MYSQL in config, restart the server, and then run this command!"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§eStarting data migration from SQLITE to MYSQL (MariaDB)... Please wait!"), true);

        Thread worker = new Thread(() -> {
            File sqliteFile = FMLPaths.CONFIGDIR.get().resolve("evocore/evodata.db").toFile();
            if (!sqliteFile.exists()) {
                sendFeedback(source, Component.literal("§cThe SQLITE database file (evodata.db) was not found!"), true);
                return;
            }

            try (Connection sqlite = DatabaseManager.openSqliteConnection(sqliteFile)) {
                Set<String> tables = new LinkedHashSet<>();
                // Known static tables first
                for (String known : new String[]{
                        "player_stats", "player_jobs", "player_homes",
                        "player_claims", "claim_player_settings", "claim_global_events", "claim_player_flags",
                        "player_keys", "player_warnings", "player_mutes", "player_ranks",
                        "player_kit_cooldowns", "daily_quests", "plot_claims", "evoprotection_rooms",
                        "player_referrals", "player_event_logs"
                }) {
                    tables.add(known);
                }
                // Discover dynamically created tables (e.g., dimension-scoped protection tables)
                try (Statement stDiscovery = sqlite.createStatement();
                     ResultSet rsTables = stDiscovery.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                    while (rsTables.next()) {
                        String tbl = rsTables.getString(1);
                        if (isAllowedTable(tbl)) {
                            tables.add(tbl);
                        }
                    }
                }

                for (String table : tables) {
                    if (!isAllowedTable(table)) continue;
                    try (Statement stmt = sqlite.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + table + "\"")) {

                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        if (colCount <= 0) continue;

                        StringBuilder cols = new StringBuilder();
                        StringBuilder params = new StringBuilder();
                        for (int i = 1; i <= colCount; i++) {
                            String colName = meta.getColumnName(i);
                            if (!colName.matches("^[A-Za-z0-9_]+$")) {
                                throw new SQLException("Unsafe column identifier: " + colName);
                            }
                            if (i > 1) {
                                cols.append(", ");
                                params.append(", ");
                            }
                            cols.append("`").append(colName).append("`");
                            params.append("?");
                        }

                        String insertQuery = "REPLACE INTO `" + table + "` (" + cols + ") VALUES (" + params + ")";

                        int count = 0;
                        synchronized (DatabaseManager.get()) {
                            Connection mysql = db.getConnection();
                            if (mysql == null) {
                                sendFeedback(source, Component.literal("§cLost connection to MySQL during migration of " + table), true);
                                return;
                            }
                            try (PreparedStatement ps = mysql.prepareStatement(insertQuery)) {
                                while (rs.next()) {
                                    for (int i = 1; i <= colCount; i++) {
                                        ps.setObject(i, rs.getObject(i));
                                    }
                                    ps.addBatch();
                                    count++;
                                    if (count % 100 == 0) ps.executeBatch();
                                }
                                ps.executeBatch();
                            }
                        }
                        final int finalCount = count;
                        final String finalTable = table;
                        sendFeedback(source, Component.literal("§aTable migrated successfully: " + finalTable + " (" + finalCount + " records)"), false);
                    } catch (SQLException e) {
                        String msg = e.getMessage() == null ? "" : e.getMessage();
                        if (!msg.contains("no such table")) {
                            final String warnMsg = "§eSkipped table " + table + ": " + msg;
                            sendFeedback(source, Component.literal(warnMsg), false);
                        }
                    }
                }
                sendFeedback(source, Component.literal("§aDATABASE MIGRATION COMPLETED SUCCESSFULLY!"), false);
            } catch (Exception e) {
                e.printStackTrace();
                sendFeedback(source, Component.literal("§cCritical error during migration! Check server console for details."), true);
            }
        }, "EvoCore-DB-Migrator");
        worker.setDaemon(true);
        worker.start();

        return 1;
    }
}
