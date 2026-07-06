package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.evocraft.evocore.bank.EvoBankManager;
import org.evocraft.evocore.bank.EvoWalletManager;
import org.evocraft.evocore.bank.EvoWalletMenu;

public class EvoWalletScreen extends AbstractContainerScreen<EvoWalletMenu> {
    private static final int BG = 0xF0040907;
    private static final int PANEL = 0xE8091B14;
    private static final int PANEL_2 = 0xAA0E2A20;
    private static final int GREEN = 0xFF23F29B;
    private static final int GREEN_SOFT = 0xFF0FA86A;
    private static final int GREEN_DARK = 0xFF08472F;
    private static final int TEXT = 0xFFEAFBF2;
    private static final int MUTED = 0xFF8EA99D;
    private static final int ERROR = 0xFFFF6B6B;
    private static final int WARN = 0xFFFFD166;

    private String lastSoundMessage = "";

    public EvoWalletScreen(EvoWalletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 352;
        imageHeight = 250;
        titleLabelY = 10000;
        inventoryLabelY = 10000;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!ClientWalletData.message.isBlank() && !ClientWalletData.message.equals(lastSoundMessage)) {
            lastSoundMessage = ClientWalletData.message;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    ClientWalletData.positive ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                    ClientWalletData.positive ? 1.05F : 0.85F
            ));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawWalletCountOverlays(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF00B1712);
        frame(graphics, x, y, imageWidth, imageHeight, 0xCC23F29B);

        panel(graphics, x + 12, y + 14, 96, 108, "WALLET CARD");
        panel(graphics, x + 124, y + 14, 216, 108, "EVO CASH");
        panel(graphics, x + 12, y + 134, 328, 104, "INVENTORY");

        drawCardPanel(graphics, x, y);
        drawCashPanel(graphics, x, y);
        drawInventoryPanel(graphics, x, y);
        drawMessage(graphics, x + 22, y + 112);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawCardPanel(GuiGraphics graphics, int x, int y) {
        graphics.drawCenteredString(font, ClientWalletData.hasCard ? "Card stored" : "No card", x + 60, y + 40,
                ClientWalletData.hasCard ? GREEN : MUTED);
        drawSlotWell(graphics, x + EvoWalletMenu.CARD_SLOT_X, y + EvoWalletMenu.CARD_SLOT_Y,
                ClientWalletData.hasCard ? GREEN : GREEN_DARK);
        graphics.drawCenteredString(font, "1 slot", x + 60, y + 86, MUTED);
    }

    private void drawCashPanel(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "Capacity", x + 137, y + 31, MUTED, false);
        graphics.drawString(font, EvoWalletManager.CASH_SLOT_LIMIT + " notes each", x + 187, y + 31, TEXT, false);

        for (int i = 0; i < EvoWalletManager.DENOMINATIONS.length; i++) {
            int col = i % 3;
            int row = i / 3;
            int sx = x + EvoWalletMenu.CASH_GRID_X + col * 42;
            int sy = y + EvoWalletMenu.CASH_GRID_Y + row * 31;
            int count = i < ClientWalletData.cashCounts.length ? ClientWalletData.cashCounts[i] : 0;
            drawSlotWell(graphics, sx, sy, count > 0 ? GREEN : GREEN_DARK);
            graphics.drawCenteredString(font, shortAmount(EvoWalletManager.DENOMINATIONS[i]), sx + 8, sy + 20, count > 0 ? TEXT : MUTED);
        }
    }

    private void drawInventoryPanel(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(graphics, x + EvoWalletMenu.INVENTORY_X + col * 18, y + EvoWalletMenu.INVENTORY_Y + row * 18, 0x664BF2A8);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(graphics, x + EvoWalletMenu.INVENTORY_X + col * 18, y + EvoWalletMenu.HOTBAR_Y, 0x884BF2A8);
        }
    }

    private void drawWalletCountOverlays(GuiGraphics graphics) {
        int x = leftPos;
        int y = topPos;
        for (int i = 0; i < EvoWalletManager.DENOMINATIONS.length; i++) {
            int count = i < ClientWalletData.cashCounts.length ? ClientWalletData.cashCounts[i] : 0;
            if (count <= 0) continue;

            int col = i % 3;
            int row = i / 3;
            int sx = x + EvoWalletMenu.CASH_GRID_X + col * 42;
            int sy = y + EvoWalletMenu.CASH_GRID_Y + row * 31;
            String label = EvoWalletManager.formatCount(count);
            int tx = sx + 17 - font.width(label);
            int ty = sy + 9;
            graphics.fill(tx - 1, ty - 1, sx + 18, ty + 9, 0xCC03100B);
            graphics.drawString(font, label, tx, ty, count >= EvoWalletManager.CASH_SLOT_LIMIT ? WARN : TEXT, false);
        }
    }

    private void drawMessage(GuiGraphics graphics, int x, int y) {
        if (!ClientWalletData.message.isBlank()) {
            graphics.drawString(font, trim(ClientWalletData.message, 304), x, y,
                    ClientWalletData.positive ? GREEN : ERROR, false);
        }
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_2);
        graphics.fill(x, y, x + w, y + 1, 0xCC23F29B);
        graphics.drawString(font, label, x + 8, y + 7, 0xFF6EF4B7, false);
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void slotFrame(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xBB03100B);
        frame(graphics, x - 1, y - 1, 18, 18, color);
    }

    private void drawSlotWell(GuiGraphics graphics, int x, int y, int color) {
        graphics.fillGradient(x - 5, y - 5, x + 21, y + 21, 0x7707140F, 0xAA020806);
        frame(graphics, x - 5, y - 5, 26, 26, 0x5523F29B);
        slotFrame(graphics, x, y, color);
    }

    private String shortAmount(int amount) {
        if (amount >= 1000) return EvoBankManager.formatAmount(amount);
        return Integer.toString(amount);
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        while (!value.isEmpty() && font.width(value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }
}
