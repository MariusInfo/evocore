package org.evocraft.evocore.teleport;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.data.PlayerStatsManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EvoCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TpaManager {

    private static class TpaRequest {
        ServerPlayer sender;
        boolean isTpaHere;
        long expireTime;

        TpaRequest(ServerPlayer sender, boolean isTpaHere) {
            this.sender = sender;
            this.isTpaHere = isTpaHere;
            this.expireTime = System.currentTimeMillis() + 60000; // Expiră în 60 de secunde
        }
    }

    private static final Map<UUID, TpaRequest> activeRequests = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Comenzi pentru accept / deny
        dispatcher.register(Commands.literal("tpaccept").executes(context -> acceptTpaRequest(context.getSource())));
        dispatcher.register(Commands.literal("tpdeny").executes(context -> denyTpaRequest(context.getSource())));

        // Comanda /tpa <jucator> - Reintrodusă pentru utilizare directă din chat
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("jucator", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "jucator");
                            sendTpaRequest(context.getSource().getPlayerOrException(), target.getName().getString(), false);
                            return 1;
                        })
                )
        );

        // Comanda /tpahere <jucator> - Reintrodusă pentru utilizare directă din chat
        dispatcher.register(Commands.literal("tpahere")
                .then(Commands.argument("jucator", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "jucator");
                            sendTpaRequest(context.getSource().getPlayerOrException(), target.getName().getString(), true);
                            return 1;
                        })
                )
        );
    }

    public static void sendTpaRequest(ServerPlayer sender, String targetName, boolean isTpaHere) {
        ServerPlayer target = sender.serverLevel().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§c[!] Jucătorul nu este online!"));
            return;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§c[!] Nu îți poți trimite cereri singur!"));
            return;
        }

        // VERIFICARE BLOCK LIST ȘI SETĂRI (Din Hub Tab 7)
        PlayerStatsManager.PlayerStats stats = PlayerStatsManager.get().getStats(target.getUUID());
        if (stats != null) {
            if (!stats.allowTPA) {
                sender.sendSystemMessage(Component.literal("§c[!] Acest jucător are cererile de teleportare dezactivate."));
                return;
            }
            if (stats.blockedPlayers.contains(sender.getUUID().toString())) {
                sender.sendSystemMessage(Component.literal("§c[!] Acest jucător te-a blocat. Nu îi poți trimite cereri."));
                return;
            }
        }

        if (activeRequests.containsKey(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§c[!] Acest jucător are deja o cerere în așteptare. Așteaptă!"));
            return;
        }

        activeRequests.put(target.getUUID(), new TpaRequest(sender, isTpaHere));

        String type = isTpaHere ? "§eTPA Here" : "§eTPA";
        sender.sendSystemMessage(Component.literal("§a[✔] Ai trimis o cerere de " + type + " §acătre §e" + target.getName().getString() + "§a."));

        target.sendSystemMessage(Component.literal("§e[!] §6" + sender.getName().getString() + " §adorește să " + (isTpaHere ? "te teleportezi la el" : "se teleporteze la tine") + "."));
        target.sendSystemMessage(Component.literal("§aFolosește §e/tpaccept §asau §c/tpdeny§a."));

        // Mesajul ascuns pentru notificarea Slide-In de pe ecran
        String hiddenMsg = "§0~EVOTPA~" + sender.getUUID().toString() + ":" + sender.getName().getString() + ":" + (isTpaHere ? "TPA_HERE" : "TPA");
        target.sendSystemMessage(Component.literal(hiddenMsg));
    }

    private static int acceptTpaRequest(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer target = source.getPlayer();
        UUID targetId = target.getUUID();

        if (!activeRequests.containsKey(targetId)) {
            target.sendSystemMessage(Component.literal("§c[!] Nu ai nicio cerere de teleportare în așteptare."));
            return 0;
        }

        TpaRequest req = activeRequests.remove(targetId);

        if (req.sender.hasDisconnected()) {
            target.sendSystemMessage(Component.literal("§c[!] Jucătorul care a trimis cererea nu mai este online."));
            return 0;
        }

        target.sendSystemMessage(Component.literal("§a[✔] Ai acceptat cererea de teleportare de la §e" + req.sender.getName().getString() + "§a."));
        req.sender.sendSystemMessage(Component.literal("§a[✔] §e" + target.getName().getString() + " §aa acceptat cererea!"));

        if (req.isTpaHere) {
            TeleportManager.queueTeleport(target, req.sender.getName().getString(), () -> {
                target.teleportTo(req.sender.serverLevel(), req.sender.getX(), req.sender.getY(), req.sender.getZ(), req.sender.getYRot(), req.sender.getXRot());
                target.sendSystemMessage(Component.literal("§aTe-ai teleportat la " + req.sender.getName().getString() + "!"));
            });
        } else {
            TeleportManager.queueTeleport(req.sender, target.getName().getString(), () -> {
                req.sender.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                req.sender.sendSystemMessage(Component.literal("§aTe-ai teleportat la " + target.getName().getString() + "!"));
            });
        }
        return 1;
    }

    private static int denyTpaRequest(CommandSourceStack source) {
        if (!source.isPlayer()) return 0;
        ServerPlayer target = source.getPlayer();
        UUID targetId = target.getUUID();

        if (!activeRequests.containsKey(targetId)) {
            target.sendSystemMessage(Component.literal("§c[!] Nu ai nicio cerere în așteptare."));
            return 0;
        }

        TpaRequest req = activeRequests.remove(targetId);
        target.sendSystemMessage(Component.literal("§c[!] Ai respins cererea de teleportare de la §e" + req.sender.getName().getString() + "§c."));

        if (!req.sender.hasDisconnected()) {
            req.sender.sendSystemMessage(Component.literal("§c[!] §e" + target.getName().getString() + " §ca respins cererea ta."));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, TpaRequest>> it = activeRequests.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, TpaRequest> entry = it.next();
                if (now > entry.getValue().expireTime) {
                    ServerPlayer target = entry.getValue().sender.serverLevel().getServer().getPlayerList().getPlayer(entry.getKey());
                    if (target != null) target.sendSystemMessage(Component.literal("§c[!] Cererea de teleportare de la §e" + entry.getValue().sender.getName().getString() + " §ca expirat."));
                    if (!entry.getValue().sender.hasDisconnected()) entry.getValue().sender.sendSystemMessage(Component.literal("§c[!] Cererea ta a expirat."));
                    it.remove();
                }
            }
        }
    }
}