package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.evocraft.evocore.data.EventLogManager;

import java.util.List;

public class EventLogCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eventlog")
                .requires(source -> hasLuckPermission(source, "evocore.eventlog.view"))
                .then(Commands.literal("recent")
                        .executes(context -> showRecent(context, defaultLimit()))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                .executes(context -> showRecent(context, IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> showPlayer(context,
                                        StringArgumentType.getString(context, "player"), defaultLimit()))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> showPlayer(context,
                                                StringArgumentType.getString(context, "player"),
                                                IntegerArgumentType.getInteger(context, "limit"))))))
                .then(Commands.literal("flush")
                        .requires(source -> hasLuckPermission(source, "evocore.eventlog.admin"))
                        .executes(context -> {
                            EventLogManager manager = EventLogManager.get();
                            if (manager == null) {
                                context.getSource().sendFailure(Component.literal("Event log is not initialized."));
                                return 0;
                            }
                            manager.flushAsync();
                            context.getSource().sendSuccess(() -> Component.literal("Event log flush queued.")
                                    .withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))
        );
    }

    private static int showRecent(CommandContext<CommandSourceStack> context, int limit) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("Event log is not initialized."));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int safeLimit = Math.min(manager.maxQueryLimit(), Math.max(1, limit));
        new Thread(() -> {
            List<EventLogManager.Entry> entries = manager.getRecent(safeLimit);
            server.execute(() -> sendEntries(source, "Recent player events", entries));
        }, "EvoCore EventLog Query").start();
        return 1;
    }

    private static int showPlayer(CommandContext<CommandSourceStack> context, String playerName, int limit) {
        EventLogManager manager = EventLogManager.get();
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("Event log is not initialized."));
            return 0;
        }

        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        int safeLimit = Math.min(manager.maxQueryLimit(), Math.max(1, limit));
        new Thread(() -> {
            List<EventLogManager.Entry> entries = manager.getByPlayer(playerName, safeLimit);
            server.execute(() -> sendEntries(source, "Event log for " + playerName, entries));
        }, "EvoCore EventLog Query").start();
        return 1;
    }

    private static void sendEntries(CommandSourceStack source, String title, List<EventLogManager.Entry> entries) {
        source.sendSuccess(() -> Component.literal(title + " (" + entries.size() + "):")
                .withStyle(ChatFormatting.GOLD), false);

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No events found.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }

        for (EventLogManager.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal(EventLogManager.formatEntry(entry))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static int defaultLimit() {
        EventLogManager manager = EventLogManager.get();
        return manager != null ? manager.defaultQueryLimit() : 20;
    }

    private static boolean hasLuckPermission(CommandSourceStack source, String permission) {
        try {
            if (source.hasPermission(2)) {
                return true;
            }
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(source.getPlayerOrException().getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        } catch (Exception e) {
            return source.hasPermission(2);
        }
    }
}
