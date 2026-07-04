package org.evocraft.evocore.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import org.evocraft.evocore.data.CrateKeyManager;

import java.util.Collection;

public class CrateCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("evocrate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("key")
                        .then(Commands.literal("give")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .executes(context -> give(context, 1))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> give(context, IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(context -> set(context, IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("crate", StringArgumentType.word())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> take(context, IntegerArgumentType.getInteger(context, "amount")))))))));

        dispatcher.register(Commands.literal("cratekey")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .then(Commands.argument("crate", StringArgumentType.word())
                                        .executes(context -> give(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> give(context, IntegerArgumentType.getInteger(context, "amount"))))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "players");
        String crate = StringArgumentType.getString(context, "crate");
        for (GameProfile profile : profiles) {
            CrateKeyManager.get().addKeys(profile.getName(), crate, amount);
        }
        String normalizedCrate = CrateKeyManager.get().normalizeCrateName(crate);
        context.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Added §e" + amount + " " + normalizedCrate + " §akey(s) to §e" + profiles.size() + " §aplayer(s)."), true);
        return profiles.size();
    }

    private static int set(CommandContext<CommandSourceStack> context, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "players");
        String crate = StringArgumentType.getString(context, "crate");
        for (GameProfile profile : profiles) {
            CrateKeyManager.get().setKeys(profile.getName(), crate, amount);
        }
        String normalizedCrate = CrateKeyManager.get().normalizeCrateName(crate);
        context.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Set §e" + normalizedCrate + " §akey(s) to §e" + amount + " §afor §e" + profiles.size() + " §aplayer(s)."), true);
        return profiles.size();
    }

    private static int take(CommandContext<CommandSourceStack> context, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "players");
        String crate = StringArgumentType.getString(context, "crate");
        for (GameProfile profile : profiles) {
            CrateKeyManager.get().takeKeys(profile.getName(), crate, amount);
        }
        String normalizedCrate = CrateKeyManager.get().normalizeCrateName(crate);
        context.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Removed up to §e" + amount + " " + normalizedCrate + " §akey(s) from §e" + profiles.size() + " §aplayer(s)."), true);
        return profiles.size();
    }
}
