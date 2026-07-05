package org.evocraft.evocore.bank;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;

@Mod.EventBusSubscriber(modid = EvoCore.MODID)
public class EvoBankEvents {

    @SubscribeEvent
    public static void onBankerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Entity target = event.getTarget();
        if (!(target instanceof Villager) || !target.getTags().contains(EvoBankManager.BANKER_TAG)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (event.getEntity() instanceof ServerPlayer player) {
            EvoBankManager.get().handleBankerInteraction(player);
        }
    }

    @SubscribeEvent
    public static void onAtmInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!EvoBankManager.get().isAtm(event.getLevel(), event.getPos())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!EvoBankManager.get().hasValidBankCard(player)) {
            player.sendSystemMessage(Component.literal("\u00A7c[EvoBank] You need your personal EvoBank card. Talk to the banker first."));
            return;
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new EvoBankAtmMenu(containerId, inventory),
                Component.literal("\u00A76\u00A7lEvoBank ATM")
        ));
    }
}
