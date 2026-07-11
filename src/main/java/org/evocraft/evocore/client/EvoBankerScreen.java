package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.evocraft.evocore.bank.EvoBankManager;
import org.evocraft.evocore.bank.EvoBankerMenu;
import org.evocraft.evocore.network.PacketHandler;

public class EvoBankerScreen extends AbstractContainerScreen<EvoBankerMenu> {
    private static final int BG = 0xF0040907;
    private static final int PANEL = 0xE8091B14;
    private static final int PANEL_2 = 0xAA0E2A20;
    private static final int GREEN = 0xFF23F29B;
    private static final int GREEN_DARK = 0xFF08472F;
    private static final int TEXT = 0xFFEAFBF2;
    private static final int MUTED = 0xFF8EA99D;
    private static final int ERROR = 0xFFFF6B6B;
    private static final int WARN = 0xFFFFD166;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private String lastSoundMessage = "";

    public EvoBankerScreen(EvoBankerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 204;
        titleLabelY = 10000;
        inventoryLabelY = 10000;
    }

    public void applyBankerState(boolean hasAccount, String ownerName, double balance, String message, boolean positive) {
        menu.updateState(hasAccount, false, ownerName, balance, message, positive);
        if (!message.isBlank() && !message.equals(lastSoundMessage)) {
            lastSoundMessage = message;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    positive ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                    positive ? 1.1F : 0.85F
            ));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, BG, 0xF00B1712);
        frame(graphics, x, y, imageWidth, imageHeight, 0xCC23F29B);

        panel(graphics, x + 12, y + 14, 276, 176, "BANKER");
        graphics.drawCenteredString(font, "EvoBank Services", x + imageWidth / 2, y + 37, GREEN);
        graphics.drawCenteredString(font, menu.hasAccount ? "Account active" : "Account missing",
                x + imageWidth / 2, y + 56, menu.hasAccount ? GREEN : WARN);

        if (menu.hasAccount) {
            graphics.drawString(font, "Account", x + 38, y + 78, MUTED, false);
            graphics.drawString(font, menu.ownerName.isBlank() ? "Unknown" : menu.ownerName, x + 96, y + 78, TEXT, false);
            graphics.drawString(font, "Balance", x + 38, y + 94, MUTED, false);
            graphics.drawString(font, EvoBankManager.formatMoney(menu.balance), x + 96, y + 94, GREEN, false);
        }

        Rect pin = pinButton(x, y);
        Rect card = cardButton(x, y);
        drawButton(graphics, pin, "RESET PIN", EvoBankManager.formatMoney(EvoBankManager.getPinResetCost()),
                menu.hasAccount, pin.contains(mouseX, mouseY));
        drawButton(graphics, card, "REPLACE CARD", EvoBankManager.formatMoney(EvoBankManager.getCardReplacementCost()),
                menu.hasAccount, card.contains(mouseX, mouseY));

        if (!menu.message.isBlank()) {
            graphics.drawCenteredString(font, trim(menu.message, 246), x + imageWidth / 2, y + 169,
                    menu.positive ? GREEN : ERROR);
        } else {
            graphics.drawCenteredString(font, "Use the ATM for deposits, withdrawals, and transfers.",
                    x + imageWidth / 2, y + 169, MUTED);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.hasAccount) {
            int x = leftPos;
            int y = topPos;
            if (pinButton(x, y).contains(mouseX, mouseY)) {
                PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankerService("pin"));
                playClick();
                return true;
            }
            if (cardButton(x, y).contains(mouseX, mouseY)) {
                PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankerService("card"));
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Rect pinButton(int x, int y) {
        return new Rect(x + 36, y + 116, 108, 28);
    }

    private Rect cardButton(int x, int y) {
        return new Rect(x + 156, y + 116, 108, 28);
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_2);
        graphics.fill(x, y, x + w, y + 1, 0xCC23F29B);
        graphics.drawString(font, label, x + 8, y + 7, 0xFF6EF4B7, false);
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String label, String detail, boolean active, boolean hover) {
        int border = active ? (hover ? 0xFF8DFFD0 : GREEN) : 0xFF365346;
        int top = active ? (hover ? 0xFF147D55 : 0xFF0E5D40) : 0xFF1A2A22;
        int bottom = active ? (hover ? 0xFF0C5239 : GREEN_DARK) : 0xFF111A16;
        graphics.fillGradient(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, top, bottom);
        frame(graphics, rect.x, rect.y, rect.w, rect.h, border);
        String topText = trim(label, rect.w - 10);
        String bottomText = trim(detail, rect.w - 10);
        int topX = rect.x + (rect.w - font.width(topText)) / 2;
        int bottomX = rect.x + (rect.w - font.width(bottomText)) / 2;
        graphics.drawString(font, topText, topX, rect.y + 6, active ? TEXT : MUTED, false);
        graphics.drawString(font, bottomText, bottomX, rect.y + 17, active ? GREEN : MUTED, false);
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        while (!value.isEmpty() && font.width(value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
