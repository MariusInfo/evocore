package org.evocraft.evocore.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.client.ClientFlyData; // Memoria cu timpul de zbor

@Mod.EventBusSubscriber(modid = EvoCore.MODID, value = Dist.CLIENT)
public class CombatNotificationHandler {

    private static boolean inCombat = false;
    private static long remainingMs = 0;
    private static long lastUpdateTime = 0;

    public static void updateCombatStatus(boolean active, long ms) {
        inCombat = active;
        remainingMs = ms;
        lastUpdateTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        // Randăm doar peste Hotbar ca să nu apară de mai multe ori pe ecran
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        // ==========================================
        // 1. INDICATORUL DE FLY (CU TIMP AVANSAT)
        // ==========================================
        boolean isTabOpen = mc.options.keyPlayerList.isDown();

        // Nu randăm dacă e deschis tab-ul ca să nu se suprapună
        if (mc.player.getAbilities().flying && !mc.player.isSpectator() && !isTabOpen) {

            // Sistem inteligent de scalare în funcție de GUI Scale
            double guiScale = mc.getWindow().getGuiScale();
            float flyScale = 1.0f;
            if (guiScale >= 4) flyScale = 0.65f;
            else if (guiScale >= 3) flyScale = 0.85f;

            g.pose().pushPose();
            g.pose().scale(flyScale, flyScale, 1.0f);

            int flyW = 125; // Puțin mai lat ca să încapă "1 oră, 50 min"
            int flyH = 34;  // Mărit pentru a intra 2 rânduri

            // Calculăm noile coordonate aplicând scalarea
            int fX = (int) (10 / flyScale);
            int fY = (int) ((h / flyScale - flyH) / 2); // Centrat pe axa Y, în stânga ecranului

            // Fundal și contur Cyan
            g.fill(fX, fY, fX + flyW, fY + flyH, 0x88000000);
            g.renderOutline(fX, fY, flyW, flyH, 0xFF55FFFF);

            // Rândul 1: Titlul
            g.drawString(mc.font, "§b☄ FLY ENABLED", fX + 21, fY + 6, 0xFFFFFF, true);

            // Rândul 2: Cronometrul inteligent citit din rețea
            int left = ClientFlyData.timeRemainingSeconds;
            String timeText;

            if (left == -1) {
                timeText = "§aUnlimited";
            } else {
                int hTime = left / 3600;
                int mTime = (left % 3600) / 60;

                // Formatăm textul în stil RPG ("1 oră, 50 min")
                if (left >= 3600) {
                    String hoursText = (hTime == 1) ? "1 hour" : hTime + " hours";
                    timeText = hoursText + (mTime > 0 ? ", " + mTime + " min" : "");
                }
                else if (left >= 60) {
                    timeText = mTime + " min";
                }
                else {
                    timeText = left + " seconds";
                }

                // Colorăm în funcție de cât timp mai are
                if (left <= 60) timeText = "§c" + timeText; // Roșu sub 1 minut
                else if (left <= 300) timeText = "§e" + timeText; // Galben sub 5 minute
                else timeText = "§a" + timeText; // Verde în rest
            }

            g.drawString(mc.font, "§fTime: " + timeText, fX + 16, fY + 20, 0xFFFFFF, true);

            g.pose().popPose();
        }

        // ==========================================
        // 2. INDICATORUL DE COMBAT LOG
        // ==========================================
        if (!inCombat) return;

        long now = System.currentTimeMillis();
        long passed = now - lastUpdateTime;
        long currentRemaining = remainingMs - passed;

        if (currentRemaining <= 0) {
            inCombat = false;
            return;
        }

        int boxWidth = 140;
        int boxHeight = 30;

        // În dreapta ecranului, deasupra hotbarului
        int x = w - boxWidth - 10;
        int y = h - boxHeight - 40;

        int bgColorStart = 0xEE000000;
        int bgColorEnd   = 0xAA000000;
        int themeColor   = 0xFFFF3333; // Roșu de combat

        // Fundal și margini roșii
        g.fillGradient(x, y, x + boxWidth, y + boxHeight, bgColorStart, bgColorEnd);
        g.fill(x, y, x + 3, y + boxHeight, themeColor);
        g.fill(x + boxWidth - 3, y, x + boxWidth, y + boxHeight, themeColor);
        g.fill(x + 3, y, x + boxWidth - 3, y + 1, themeColor);
        g.fill(x + 3, y + boxHeight - 1, x + boxWidth - 3, y + boxHeight, themeColor);

        // Calculăm secundele rămase
        float seconds = currentRemaining / 1000.0f;
        String formattedTime = String.format("%.1fs", seconds);

        // Desenăm textul de combat
        g.drawString(mc.font, "§c§l⚔ IN COMBAT ⚔", x + 25, y + 5, 0xFFFFFF, true);
        g.drawString(mc.font, "§eTime: §c" + formattedTime, x + 35, y + 17, 0xFFFFFF, true);
    }
}
