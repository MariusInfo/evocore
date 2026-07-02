package org.evocraft.evocore.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TempBanCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Format comanda: /tempban <NumeJucator> <Timp> <Motiv>
        dispatcher.register(Commands.literal("tempban")
                .requires(source -> BroadcastCommand.hasLuckPermission(source, "evocore.ban"))
                .then(Commands.argument("jucatori", GameProfileArgument.gameProfile())
                        .then(Commands.argument("timp", StringArgumentType.word()) // Ex: 1d, 12h, 30m
                                .then(Commands.argument("motiv", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "jucatori");
                                            String timeStr = StringArgumentType.getString(context, "timp");
                                            String reason = StringArgumentType.getString(context, "motiv");
                                            String adminName = source.getTextName();

                                            // 1. Calculăm timpul în milisecunde
                                            long durationMillis = parseTime(timeStr);
                                            if (durationMillis <= 0) {
                                                source.sendFailure(Component.literal("§cFormat de timp invalid! Folosește: 1d, 12h, 30m, 10s"));
                                                return 0;
                                            }

                                            Date expireDate = new Date(System.currentTimeMillis() + durationMillis);
                                            UserBanList banList = source.getServer().getPlayerList().getBans();

                                            // 2. Aplicăm ban-ul pe toate profilele găsite
                                            int bannedCount = 0;
                                            for (GameProfile profile : targets) {
                                                UserBanListEntry banEntry = new UserBanListEntry(
                                                        profile,
                                                        new Date(),
                                                        adminName,
                                                        expireDate,
                                                        reason
                                                );

                                                banList.add(banEntry);
                                                bannedCount++;

                                                // Dacă e online, îl dăm afară direct cu motivul pe ecran
                                                ServerPlayer onlinePlayer = source.getServer().getPlayerList().getPlayer(profile.getId());
                                                if (onlinePlayer != null) {
                                                    onlinePlayer.connection.disconnect(Component.literal(
                                                            "§c§lAI FOST BANAT TEMPORAR!\n\n" +
                                                                    "§7Motiv: §f" + reason + "\n" +
                                                                    "§7Expiră la: §e" + expireDate.toString() + "\n" +
                                                                    "§7Banat de: §b" + adminName
                                                    ));
                                                }
                                            }

                                            if (bannedCount > 0) {
                                                // AICI E REZOLVAREA EROARII: Înghețăm valoarea pentru a o putea folosi în Lambda
                                                final int finalBannedCount = bannedCount;
                                                source.sendSuccess(() -> Component.literal("§a✔ Ai dat tempban la " + finalBannedCount + " jucător(i) pentru " + timeStr + "."), true);
                                            }

                                            return bannedCount;
                                        })
                                )))
        );
    }

    // Funcție de transformare "1d", "5h", "30m" în Milisecunde
    private static long parseTime(String timeStr) {
        long totalMillis = 0;
        Pattern pattern = Pattern.compile("(\\d+)([dhms])");
        Matcher matcher = pattern.matcher(timeStr.toLowerCase());

        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "d": totalMillis += value * 24L * 60 * 60 * 1000; break;
                case "h": totalMillis += value * 60 * 60 * 1000; break;
                case "m": totalMillis += value * 60 * 1000; break;
                case "s": totalMillis += value * 1000; break;
            }
        }
        return totalMillis;
    }
}