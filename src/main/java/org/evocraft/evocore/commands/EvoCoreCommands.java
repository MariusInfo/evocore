package org.evocraft.evocore.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.data.TranslationManager;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class EvoCoreCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. Înregistrăm comanda de bază /evo
        dispatcher.register(Commands.literal("evo")
                .requires(s -> s.hasPermission(2)) // Necesită OP
                .then(Commands.literal("reload").executes(ctx -> {
                    TranslationManager.initialize();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[EvoCore] Core message configuration was reloaded!"), true);
                    return 1;
                }))
        );

        // =========================================
        // 2. AICI ÎNREGISTRĂM TOATE CELELALTE COMENZI!
        // =========================================
        EconomyCommands.register(dispatcher);
        VanishCommand.register(dispatcher);  // Acum vor merge /bani și /pay!

        // Dacă ai făcut și celelalte comenzi pe care le-am discutat, trebuie chemate tot aici:
        // GodCommand.register(dispatcher);
        // InvseeCommand.register(dispatcher);
        // FlyCommand.register(dispatcher);
        // AdminCommands.register(dispatcher); // (Aici aveai /evokit și /evowarp)
    }
}
