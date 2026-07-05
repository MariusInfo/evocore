package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.evocraft.evocore.bank.EvoBankAtmMenu;
import org.evocraft.evocore.bank.EvoBankManager;
import org.evocraft.evocore.network.PacketHandler;
import org.lwjgl.glfw.GLFW;

public class EvoBankAtmScreen extends AbstractContainerScreen<EvoBankAtmMenu> {
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

    private enum Page {
        MAIN,
        WITHDRAW,
        DEPOSIT,
        TRANSFER
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private Page page = Page.MAIN;
    private EditBox pinBox;
    private EditBox targetBox;
    private EditBox amountBox;
    private String lastSoundMessage = "";

    public EvoBankAtmScreen(EvoBankAtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 352;
        imageHeight = 282;
        titleLabelY = 10000;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        pinBox = new EditBox(font, x + 30, y + 121, 66, 18, Component.literal("PIN"));
        pinBox.setMaxLength(6);
        pinBox.setFilter(value -> value.matches("\\d{0,6}"));
        pinBox.setFormatter((value, offset) -> value.isEmpty()
                ? FormattedCharSequence.EMPTY
                : FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
        addRenderableWidget(pinBox);

        targetBox = new EditBox(font, x + 137, y + 94, 92, 18, Component.literal("Player"));
        targetBox.setMaxLength(16);
        addRenderableWidget(targetBox);

        amountBox = new EditBox(font, x + 235, y + 94, 72, 18, Component.literal("Amount"));
        amountBox.setMaxLength(12);
        amountBox.setFilter(value -> value.matches("\\d{0,9}(\\.\\d{0,2})?"));
        addRenderableWidget(amountBox);

        applyStateFromClientData(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        applyStateFromClientData(true);
    }

    public void applyStateFromClientData(boolean playSound) {
        if (!ClientBankAtmData.authenticated) {
            page = Page.MAIN;
        }

        boolean pinVisible = ClientBankAtmData.hasCard && ClientBankAtmData.cardValid && !ClientBankAtmData.authenticated;
        pinBox.visible = pinVisible;
        pinBox.setEditable(pinVisible);

        boolean transferVisible = ClientBankAtmData.authenticated && page == Page.TRANSFER;
        targetBox.visible = transferVisible;
        targetBox.setEditable(transferVisible);
        amountBox.visible = transferVisible;
        amountBox.setEditable(transferVisible);
        menu.setDepositSlotVisible(ClientBankAtmData.authenticated && page == Page.DEPOSIT);

        if (playSound && !ClientBankAtmData.message.isBlank() && !ClientBankAtmData.message.equals(lastSoundMessage)) {
            lastSoundMessage = ClientBankAtmData.message;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    ClientBankAtmData.positive ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                    ClientBankAtmData.positive ? 1.15F : 0.85F
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

        drawCardPanel(graphics, x, y);
        drawContentPanel(graphics, x, y, mouseX, mouseY);
        drawInventoryPanel(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && page != Page.DEPOSIT && depositSlotArea(leftPos, topPos).contains(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && handleCustomClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        EditBox focused = focusedTextBox();
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitFocusedAction();
                return true;
            }
            focused.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        EditBox focused = focusedTextBox();
        if (focused != null) {
            return focused.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean handleCustomClick(double mouseX, double mouseY) {
        int x = leftPos;
        int y = topPos;

        if (pinButton(x, y).contains(mouseX, mouseY) && pinBox.visible) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmPin(pinBox.getValue()));
            pinBox.setValue("");
            playClick();
            return true;
        }

        if (!ClientBankAtmData.authenticated) return false;

        if (backButton(x, y).contains(mouseX, mouseY) && page != Page.MAIN) {
            page = Page.MAIN;
            applyStateFromClientData(false);
            playClick();
            return true;
        }

        if (page == Page.MAIN) {
            if (mainButton(x, y, 0).contains(mouseX, mouseY)) {
                page = Page.DEPOSIT;
                applyStateFromClientData(false);
                playClick();
                return true;
            }
            if (mainButton(x, y, 1).contains(mouseX, mouseY)) {
                page = Page.WITHDRAW;
                applyStateFromClientData(false);
                playClick();
                return true;
            }
            if (mainButton(x, y, 2).contains(mouseX, mouseY)) {
                page = Page.TRANSFER;
                applyStateFromClientData(false);
                playClick();
                return true;
            }
        }

        if (page == Page.WITHDRAW) {
            int[] amounts = EvoBankAtmMenu.WITHDRAW_AMOUNTS;
            for (int i = 0; i < amounts.length; i++) {
                if (withdrawButton(x, y, i).contains(mouseX, mouseY)) {
                    PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmWithdraw(amounts[i]));
                    playClick();
                    return true;
                }
            }
        }

        if (page == Page.DEPOSIT && depositButton(x, y).contains(mouseX, mouseY)) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmDeposit());
            playClick();
            return true;
        }

        if (page == Page.TRANSFER && transferButton(x, y).contains(mouseX, mouseY)) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmTransfer(targetBox.getValue(), parseAmount(amountBox.getValue())));
            playClick();
            return true;
        }

        return false;
    }

    private void drawCardPanel(GuiGraphics graphics, int x, int y) {
        panel(graphics, x + 12, y + 14, 102, 162, "CARD ACCESS");
        graphics.drawCenteredString(font, "Status", x + 63, y + 37, MUTED);
        drawStatusLine(graphics, x + 63, y + 50, cardStatus(), statusColor());
        drawSlotWell(graphics, x + EvoBankAtmMenu.CARD_SLOT_X, y + EvoBankAtmMenu.CARD_SLOT_Y, ClientBankAtmData.cardValid ? GREEN : GREEN_DARK);

        if (ClientBankAtmData.authenticated && page == Page.DEPOSIT) {
            graphics.drawCenteredString(font, "Cash slot", x + 63, y + 116, TEXT);
            drawSlotWell(graphics, x + EvoBankAtmMenu.DEPOSIT_SLOT_X, y + EvoBankAtmMenu.DEPOSIT_SLOT_Y, GREEN);
        } else if (pinBox.visible) {
            graphics.drawCenteredString(font, ClientBankAtmData.hasPin ? "Enter PIN" : "Set PIN", x + 63, y + 108, MUTED);
            drawButton(graphics, pinButton(x, y), ClientBankAtmData.hasPin ? "UNLOCK" : "SET PIN", true, false);
        } else {
            graphics.drawCenteredString(font, "Insert a card", x + 63, y + 112, MUTED);
            graphics.drawCenteredString(font, "to continue", x + 63, y + 125, MUTED);
        }
    }

    private void drawContentPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        panel(graphics, x + 124, y + 14, 216, 162, pageTitle());

        if (!ClientBankAtmData.authenticated) {
            drawLockedContent(graphics, x, y);
            return;
        }

        graphics.drawString(font, "Account", x + 138, y + 31, MUTED, false);
        graphics.drawString(font, ClientBankAtmData.ownerName, x + 185, y + 31, TEXT, false);
        graphics.drawString(font, "Balance", x + 138, y + 46, MUTED, false);
        graphics.drawString(font, EvoBankManager.formatAmount(ClientBankAtmData.balance) + " Evo Cash", x + 185, y + 46, GREEN, false);

        if (page != Page.MAIN) {
            drawButton(graphics, backButton(x, y), "< BACK", true, backButton(x, y).contains(mouseX, mouseY));
        }

        switch (page) {
            case MAIN -> drawMainMenu(graphics, x, y, mouseX, mouseY);
            case WITHDRAW -> drawWithdrawPage(graphics, x, y, mouseX, mouseY);
            case DEPOSIT -> drawDepositPage(graphics, x, y, mouseX, mouseY);
            case TRANSFER -> drawTransferPage(graphics, x, y, mouseX, mouseY);
        }

        drawMessage(graphics, x + 135, y + 164);
    }

    private void drawLockedContent(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "Insert a valid card and unlock it", x + 139, y + 55, TEXT, false);
        graphics.drawString(font, "with the PIN to access banking.", x + 139, y + 68, MUTED, false);
        graphics.drawString(font, "Lost PIN/Card?", x + 139, y + 94, WARN, false);
        graphics.drawString(font, "Talk to the EvoBank NPC.", x + 139, y + 108, MUTED, false);
        drawMessage(graphics, x + 139, y + 145);
    }

    private void drawMainMenu(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawButton(graphics, mainButton(x, y, 0), "DEPOSIT", true, mainButton(x, y, 0).contains(mouseX, mouseY));
        drawButton(graphics, mainButton(x, y, 1), "WITHDRAW", true, mainButton(x, y, 1).contains(mouseX, mouseY));
        drawButton(graphics, mainButton(x, y, 2), "TRANSFER", true, mainButton(x, y, 2).contains(mouseX, mouseY));
    }

    private void drawWithdrawPage(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Select amount", x + 139, y + 67, MUTED, false);
        int[] amounts = EvoBankAtmMenu.WITHDRAW_AMOUNTS;
        for (int i = 0; i < amounts.length; i++) {
            Rect rect = withdrawButton(x, y, i);
            drawButton(graphics, rect, shortAmount(amounts[i]), true, rect.contains(mouseX, mouseY));
        }
    }

    private void drawDepositPage(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Drag Evo Cash into the cash slot", x + 139, y + 72, TEXT, false);
        graphics.drawString(font, "then confirm the deposit.", x + 139, y + 85, MUTED, false);
        Rect rect = depositButton(x, y);
        drawButton(graphics, rect, "CONFIRM DEPOSIT", true, rect.contains(mouseX, mouseY));
    }

    private void drawTransferPage(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Player", x + 138, y + 81, MUTED, false);
        graphics.drawString(font, "Amount", x + 236, y + 81, MUTED, false);
        Rect rect = transferButton(x, y);
        drawButton(graphics, rect, "SEND TRANSFER", true, rect.contains(mouseX, mouseY));
    }

    private void drawInventoryPanel(GuiGraphics graphics, int x, int y) {
        panel(graphics, x + 12, y + 184, 328, 86, "INVENTORY");
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotFrame(graphics, x + EvoBankAtmMenu.INVENTORY_X + col * 18, y + EvoBankAtmMenu.INVENTORY_Y + row * 18, 0x664BF2A8);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotFrame(graphics, x + EvoBankAtmMenu.INVENTORY_X + col * 18, y + EvoBankAtmMenu.HOTBAR_Y, 0x884BF2A8);
        }
    }

    private void drawMessage(GuiGraphics graphics, int x, int y) {
        if (!ClientBankAtmData.message.isBlank()) {
            graphics.drawString(font, trim(ClientBankAtmData.message, 190), x, y, ClientBankAtmData.positive ? GREEN : ERROR, false);
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

    private void drawStatusLine(GuiGraphics graphics, int x, int y, String label, int color) {
        int totalWidth = 12 + font.width(label);
        int startX = x - totalWidth / 2;
        graphics.fill(startX, y + 3, startX + 5, y + 8, color);
        graphics.drawString(font, label, startX + 12, y, color, false);
    }

    private void drawButton(GuiGraphics graphics, Rect rect, String label, boolean active, boolean hover) {
        int border = active ? (hover ? 0xFF8DFFD0 : GREEN) : 0xFF365346;
        int top = active ? (hover ? 0xFF147D55 : 0xFF0E5D40) : 0xFF1A2A22;
        int bottom = active ? (hover ? 0xFF0C5239 : GREEN_DARK) : 0xFF111A16;
        graphics.fillGradient(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, top, bottom);
        frame(graphics, rect.x, rect.y, rect.w, rect.h, border);
        int tx = rect.x + (rect.w - font.width(label)) / 2;
        int ty = rect.y + (rect.h - 8) / 2;
        graphics.drawString(font, label, tx, ty, active ? TEXT : MUTED, false);
    }

    private Rect pinButton(int x, int y) {
        return new Rect(x + 30, y + 147, 66, 20);
    }

    private Rect backButton(int x, int y) {
        return new Rect(x + 272, y + 21, 54, 18);
    }

    private Rect mainButton(int x, int y, int index) {
        return new Rect(x + 139, y + 70 + index * 31, 172, 25);
    }

    private Rect withdrawButton(int x, int y, int index) {
        return new Rect(x + 139 + (index % 4) * 45, y + 87 + (index / 4) * 28, 40, 23);
    }

    private Rect depositButton(int x, int y) {
        return new Rect(x + 158, y + 118, 132, 26);
    }

    private Rect transferButton(int x, int y) {
        return new Rect(x + 181, y + 124, 110, 24);
    }

    private Rect depositSlotArea(int x, int y) {
        return new Rect(x + EvoBankAtmMenu.DEPOSIT_SLOT_X - 2, y + EvoBankAtmMenu.DEPOSIT_SLOT_Y - 2, 22, 22);
    }

    private String pageTitle() {
        if (!ClientBankAtmData.authenticated) return "AUTHENTICATION";
        return switch (page) {
            case MAIN -> "MAIN MENU";
            case WITHDRAW -> "WITHDRAW";
            case DEPOSIT -> "DEPOSIT";
            case TRANSFER -> "TRANSFER";
        };
    }

    private String cardStatus() {
        if (!ClientBankAtmData.hasCard) return "No card";
        if (!ClientBankAtmData.cardValid) return "Invalid card";
        if (!ClientBankAtmData.hasPin) return "Set PIN";
        return ClientBankAtmData.authenticated ? "Unlocked" : "PIN required";
    }

    private int statusColor() {
        if (!ClientBankAtmData.hasCard) return MUTED;
        if (!ClientBankAtmData.cardValid) return ERROR;
        return ClientBankAtmData.authenticated ? GREEN : WARN;
    }

    private String shortAmount(int amount) {
        if (amount >= 1000) return (amount / 1000) + "K";
        return Integer.toString(amount);
    }

    private double parseAmount(String value) {
        try {
            return value == null || value.isBlank() ? 0.0D : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private EditBox focusedTextBox() {
        if (pinBox != null && pinBox.visible && pinBox.isFocused()) return pinBox;
        if (targetBox != null && targetBox.visible && targetBox.isFocused()) return targetBox;
        if (amountBox != null && amountBox.visible && amountBox.isFocused()) return amountBox;
        return null;
    }

    private void submitFocusedAction() {
        if (pinBox != null && pinBox.visible && pinBox.isFocused()) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmPin(pinBox.getValue()));
            pinBox.setValue("");
            playClick();
            return;
        }
        if (page == Page.TRANSFER && (targetBox.isFocused() || amountBox.isFocused())) {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmTransfer(targetBox.getValue(), parseAmount(amountBox.getValue())));
            playClick();
        }
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
