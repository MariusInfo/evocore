package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "evocore", value = Dist.CLIENT)
public class BroadcastOverlay {
    private static final int MAX_TICKS = 120;
    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_TICKS = 14;

    private static String currentMessage = "";
    private static int displayTicks = 0;

    private static int cachedScreenWidth = -1;
    private static int cachedBoxWidth = 220;
    private static int cachedBoxHeight = 42;
    private static final List<String> cachedLines = new ArrayList<>();

    public static void showMessage(String msg) {
        currentMessage = msg == null ? "" : msg.trim();
        displayTicks = currentMessage.isEmpty() ? 0 : MAX_TICKS;
        cachedScreenWidth = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && displayTicks > 0) {
            displayTicks--;
        }
    }

    @SubscribeEvent
    public static void onRenderHUD(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (displayTicks <= 0 || currentMessage.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();

        rebuildCache(mc, screenWidth);

        float fadeIn = Mth.clamp((MAX_TICKS - displayTicks) / (float) FADE_IN_TICKS, 0.0f, 1.0f);
        float fadeOut = Mth.clamp(displayTicks / (float) FADE_OUT_TICKS, 0.0f, 1.0f);
        float visibility = easeOut(Math.min(fadeIn, fadeOut));
        int alpha = Mth.clamp((int) (visibility * 235.0f), 0, 235);
        if (alpha <= 4) return;

        int x = (screenWidth - cachedBoxWidth) / 2;
        int y = 14 - (int) ((1.0f - visibility) * 26.0f);

        int shadowAlpha = Math.max(30, alpha / 3);
        g.fill(x + 2, y + 3, x + cachedBoxWidth + 2, y + cachedBoxHeight + 3, argb(shadowAlpha, 0x000000));
        g.fillGradient(x, y, x + cachedBoxWidth, y + cachedBoxHeight, argb(alpha, 0x121821), argb(alpha, 0x070A0F));
        g.fill(x, y, x + cachedBoxWidth, y + 1, argb(alpha, 0xF0C75E));
        g.fill(x, y + cachedBoxHeight - 1, x + cachedBoxWidth, y + cachedBoxHeight, argb(alpha / 2, 0x263241));
        g.fill(x, y, x + 3, y + cachedBoxHeight, argb(alpha, 0xE34A4A));
        g.renderOutline(x, y, cachedBoxWidth, cachedBoxHeight, argb(Math.min(180, alpha), 0x2D3A48));

        int titleColor = argb(alpha, 0xF0C75E);
        int textColor = argb(alpha, 0xF4F7FB);
        g.drawString(mc.font, "SERVER ANNOUNCEMENT", x + 16, y + 7, titleColor, false);

        int lineY = y + 21;
        for (String line : cachedLines) {
            g.drawString(mc.font, line, x + 16, lineY, textColor, false);
            lineY += 10;
        }
    }

    private static void rebuildCache(Minecraft mc, int screenWidth) {
        if (cachedScreenWidth == screenWidth) return;

        cachedScreenWidth = screenWidth;
        cachedLines.clear();

        int maxTextWidth = Math.max(120, Math.min(360, screenWidth - 70));
        String remaining = currentMessage;
        for (int i = 0; i < 2 && !remaining.isBlank(); i++) {
            String line = mc.font.plainSubstrByWidth(remaining, maxTextWidth);
            if (line.isBlank()) break;

            remaining = remaining.substring(line.length()).stripLeading();
            if (i == 1 && !remaining.isBlank()) {
                line = addEllipsis(mc, line, maxTextWidth);
            }
            cachedLines.add(line);
        }

        if (cachedLines.isEmpty()) {
            cachedLines.add(currentMessage);
        }

        int widest = mc.font.width("SERVER ANNOUNCEMENT");
        for (String line : cachedLines) {
            widest = Math.max(widest, mc.font.width(line));
        }

        int maxBoxWidth = Math.max(170, screenWidth - 24);
        cachedBoxWidth = Math.min(maxBoxWidth, Math.max(220, widest + 44));
        cachedBoxHeight = 31 + cachedLines.size() * 10;
    }

    private static String addEllipsis(Minecraft mc, String line, int maxTextWidth) {
        String ellipsis = "...";
        String trimmed = line.stripTrailing();
        while (!trimmed.isEmpty() && mc.font.width(trimmed + ellipsis) > maxTextWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed + ellipsis;
    }

    private static float easeOut(float value) {
        float clamped = Mth.clamp(value, 0.0f, 1.0f);
        return 1.0f - (1.0f - clamped) * (1.0f - clamped);
    }

    private static int argb(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }
}
