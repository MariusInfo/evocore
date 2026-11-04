package org.evocraft.evocore.database;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.sql.*;

public class MigrateDatabaseCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evocore")
                .then(Commands.literal("migratedb")
                        .requires(src -> src.hasPermission(3))
                        .executes(MigrateDatabaseCommand::executeMigration)
                )
        );
    }

    private static int executeMigration(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DatabaseManager db = DatabaseManager.get();
        if (db.type.equals("SQLITE")) {
            source.sendFailure(Component.literal("§cCurrent database type is SQLITE. Change type to MYSQL in config, restart the server, and then run this command!"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§eStarting data migration from SQLITE to MYSQL (MariaDB)... Please wait!"), true);

        new Thread(() -> {
            File sqliteFile = FMLPaths.CONFIGDIR.get().resolve("evocore/evodata.db").toFile();
            if (!sqliteFile.exists()) {
                source.sendFailure(Component.literal("§cThe SQLITE database file (evodata.db) was not found!"));
                return;
            }

            try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {
                Connection mysql = db.getConnection();
                
                String[] tables = {
                    "player_stats", "player_jobs", "player_homes", 
                    "player_claims", "claim_player_settings", 
                    "claim_global_events", "player_keys"
                };

                for (String table : tables) {
                    try (Statement stmt = sqlite.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {
                        
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        
                        String cols = "";
                        String params = "";
                        for (int i = 1; i <= colCount; i++) {
                            cols += meta.getColumnName(i) + (i == colCount ? "" : ", ");
                            params += "?" + (i == colCount ? "" : ", ");
                        }
                        
                        String insertQuery = "REPLACE INTO " + table + " (" + cols + ") VALUES (" + params + ")";
                        
                        try (PreparedStatement ps = mysql.prepareStatement(insertQuery)) {
                            int count = 0;
                            while (rs.next()) {
                                for (int i = 1; i <= colCount; i++) {
                                    ps.setObject(i, rs.getObject(i));
                                }
                                ps.addBatch();
                                count++;
                                if (count % 100 == 0) ps.executeBatch();
                            }
                            ps.executeBatch(); // flush remaining
                            final int finalCount = count;
                            source.sendSuccess(() -> Component.literal("§aTable migrated successfully: " + table + " (" + finalCount + " records)"), true);
                        }
                    } catch (SQLException ignore) {
                        // Table may not exist in sqlite, ignore safely
                    }
                }
                source.sendSuccess(() -> Component.literal("§aDATABASE MIGRATION COMPLETED SUCCESSFULLY!"), true);
            } catch (Exception e) {
                e.printStackTrace();
                source.sendFailure(Component.literal("§cCritical error during migration! Check server console for details."));
            }
        }).start();

        return 1;
    }
}
