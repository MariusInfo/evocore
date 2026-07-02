package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "evocore", value = Dist.CLIENT)
public class BroadcastOverlay {
    private static String currentMessage = "";
    private static int displayTicks = 0;
    private static final int MAX_TICKS = 120; // Stă 6 secunde pe ecran (20 ticks = 1 sec)

    // Metoda apelată de pachetul de la server
    public static void showMessage(String msg) {
        currentMessage = msg;
        displayTicks = MAX_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && displayTicks > 0) {
            displayTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiOverlayEvent.Post event) {
        if (displayTicks <= 0 || currentMessage.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Calculăm animația de glisare (Slide In / Slide Out)
        int yOffset = 15; // Poziția normală
        if (displayTicks > MAX_TICKS - 15) {
            // Glisează în jos la început
            yOffset = -20 + (MAX_TICKS - displayTicks) * 2;
        } else if (displayTicks < 15) {
            // Glisează înapoi în sus la final
            yOffset = 15 - (15 - displayTicks) * 2;
        }

        int textWidth = mc.font.width("ANUNȚ: " + currentMessage);
        int boxWidth = textWidth + 30;
        int x = (screenWidth - boxWidth) / 2;
        int y = yOffset;

        // Desenăm interfața (Background negru transparent)
        g.fill(x, y, x + boxWidth, y + 20, 0xCC080D08);

        // Desenăm o dungă roșie elegantă în stânga și dreapta
        g.fill(x, y, x + 2, y + 20, 0xFFE53935);
        g.fill(x + boxWidth - 2, y, x + boxWidth, y + 20, 0xFFE53935);

        // Desenăm textul
        g.drawCenteredString(mc.font, "§c§lANUNȚ: §f" + currentMessage, screenWidth / 2, y + 6, 0xFFFFFF);
    }
}