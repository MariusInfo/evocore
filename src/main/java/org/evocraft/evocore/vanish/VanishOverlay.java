package org.evocraft.evocore.vanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;

@Mod.EventBusSubscriber(modid = EvoCore.MODID, value = Dist.CLIENT)
public class VanishOverlay {

    public static boolean isVanished = false;

    @SubscribeEvent
    public static void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!isVanished) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();

        double guiScale = mc.getWindow().getGuiScale();
        float currentScale = 1.0f;
        if (guiScale >= 3) currentScale = 0.85f;
        if (guiScale >= 4) currentScale = 0.70f;

        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        int boxWidth = 140;
        int boxHeight = 25;

        g.pose().pushPose();

        float scaledX = (w / currentScale) - boxWidth - 10;
        float scaledY = (h / currentScale) - boxHeight - 80;

        g.pose().scale(currentScale, currentScale, 1.0f);

        int x = (int) scaledX;
        int y = (int) scaledY;

        int bgColorStart = 0xEE000000;
        int bgColorEnd   = 0xAA000000;
        int themeColor   = 0xFF00E055;

        g.fillGradient(x, y, x + boxWidth, y + boxHeight, bgColorStart, bgColorEnd);
        g.fill(x, y, x + 3, y + boxHeight, themeColor);
        g.fill(x + boxWidth - 3, y, x + boxWidth, y + boxHeight, themeColor);
        g.fill(x + 3, y, x + boxWidth - 3, y + 1, themeColor);
        g.fill(x + 3, y + boxHeight - 1, x + boxWidth - 3, y + boxHeight, themeColor);

        g.drawCenteredString(mc.font, "§a§l👻 EȘTI INVIZIBIL", x + boxWidth / 2, y + 8, 0xFFFFFF);

        g.pose().popPose();
    }
}