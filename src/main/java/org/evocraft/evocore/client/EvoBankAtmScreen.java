package org.evocraft.evocore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.evocraft.evocore.bank.EvoBankAtmMenu;
import org.evocraft.evocore.bank.EvoBankManager;
import org.evocraft.evocore.network.PacketHandler;

import java.util.ArrayList;
import java.util.List;

public class EvoBankAtmScreen extends AbstractContainerScreen<EvoBankAtmMenu> {
    private static final int PANEL = 0xEE07110D;
    private static final int PANEL_SOFT = 0xAA0D2119;
    private static final int GREEN = 0xFF21E68A;
    private static final int GREEN_DARK = 0xFF0B6F47;
    private static final int TEXT = 0xFFEAFBF2;
    private static final int MUTED = 0xFF8BA99A;
    private static final int ERROR = 0xFFFF6B6B;

    private final List<Button> withdrawButtons = new ArrayList<>();
    private EditBox pinBox;
    private EditBox targetBox;
    private EditBox amountBox;
    private Button pinButton;
    private Button depositButton;
    private Button transferButton;
    private float pulse;
    private String lastSoundMessage = "";

    public EvoBankAtmScreen(EvoBankAtmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        withdrawButtons.clear();

        int x = leftPos;
        int y = topPos;

        pinBox = new EditBox(font, x + 22, y + 106, 76, 18, Component.literal("PIN"));
        pinBox.setMaxLength(6);
        pinBox.setFilter(value -> value.matches("\\d{0,6}"));
        addRenderableWidget(pinBox);

        pinButton = Button.builder(Component.literal("Unlock"), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmPin(pinBox.getValue()));
            pinBox.setValue("");
            playClick();
        }).bounds(x + 22, y + 128, 76, 20).build();
        addRenderableWidget(pinButton);

        int[] amounts = EvoBankAtmMenu.WITHDRAW_AMOUNTS;
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            int bx = x + 124 + (i % 4) * 45;
            int by = y + 73 + (i / 4) * 23;
            Button button = Button.builder(Component.literal(shortAmount(amount)), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmWithdraw(amount));
                playClick();
            }).bounds(bx, by, 42, 20).build();
            withdrawButtons.add(button);
            addRenderableWidget(button);
        }

        depositButton = Button.builder(Component.literal("Deposit"), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmDeposit());
            playClick();
        }).bounds(x + 22, y + 158, 76, 20).build();
        addRenderableWidget(depositButton);

        targetBox = new EditBox(font, x + 123, y + 143, 78, 18, Component.literal("Player"));
        targetBox.setMaxLength(16);
        addRenderableWidget(targetBox);

        amountBox = new EditBox(font, x + 206, y + 143, 58, 18, Component.literal("Amount"));
        amountBox.setMaxLength(12);
        amountBox.setFilter(value -> value.matches("\\d{0,9}(\\.\\d{0,2})?"));
        addRenderableWidget(amountBox);

        transferButton = Button.builder(Component.literal("Send"), button -> {
            double amount = parseAmount(amountBox.getValue());
            PacketHandler.INSTANCE.sendToServer(new PacketHandler.C2S_EvoBankAtmTransfer(targetBox.getValue(), amount));
            playClick();
        }).bounds(x + 269, y + 142, 39, 20).build();
        addRenderableWidget(transferButton);

        applyStateFromClientData(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        pulse += 0.04F;
        applyStateFromClientData(true);
    }

    public void applyStateFromClientData(boolean playSound) {
        boolean needsPin = ClientBankAtmData.hasCard && ClientBankAtmData.cardValid && !ClientBankAtmData.authenticated;
        boolean active = ClientBankAtmData.authenticated;

        if (pinBox != null) {
            pinBox.visible = needsPin;
            pinBox.setEditable(needsPin);
        }
        if (pinButton != null) {
            pinButton.visible = needsPin;
            pinButton.active = needsPin;
            pinButton.setMessage(Component.literal(ClientBankAtmData.hasPin ? "Unlock" : "Set PIN"));
        }
        if (depositButton != null) {
            depositButton.visible = active;
            depositButton.active = active;
        }
        if (transferButton != null) {
            transferButton.visible = active;
            transferButton.active = active;
        }
        if (targetBox != null) {
            targetBox.visible = active;
            targetBox.setEditable(active);
        }
        if (amountBox != null) {
            amountBox.visible = active;
            amountBox.setEditable(active);
        }
        for (Button button : withdrawButtons) {
            button.visible = active;
            button.active = active;
        }

        if (playSound && !ClientBankAtmData.message.isBlank() && !ClientBankAtmData.message.equals(lastSoundMessage)) {
            lastSoundMessage = ClientBankAtmData.message;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    ClientBankAtmData.positive ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                    ClientBankAtmData.positive ? 1.15F : 0.8F
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
        int glow = 45 + (int) (Math.sin(pulse) * 18.0F);

        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xF0050A08, 0xF0101B14);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, 0xFF31F2A1);
        graphics.fill(x + 1, y + imageHeight - 2, x + imageWidth - 1, y + imageHeight - 1, 0xAA31F2A1);
        graphics.fill(x + 1, y + 1, x + 2, y + imageHeight - 1, 0xAA31F2A1);
        graphics.fill(x + imageWidth - 2, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xAA31F2A1);
        graphics.fillGradient(x + 116, y + 12, x + 304, y + 40, (glow << 24) | 0x0021E68A, 0x00111111);

        drawPanel(graphics, x + 12, y + 15, 98, 208, "CARD");
        drawPanel(graphics, x + 118, y + 15, 190, 109, "WITHDRAWALS");
        drawPanel(graphics, x + 118, y + 130, 190, 42, "TRANSFER");
        drawPanel(graphics, x + 118, y + 178, 190, 45, "INVENTORY");

        graphics.drawString(font, "EvoBank ATM", x + 20, y + 23, GREEN, false);
        graphics.drawString(font, cardStatus(), x + 22, y + 51, statusColor(), false);
        graphics.drawString(font, "Card", x + 22, y + 65, MUTED, false);
        graphics.drawString(font, "Cash deposit", x + 22, y + 126, MUTED, false);
        drawSlotFrame(graphics, x + EvoBankAtmMenu.CARD_SLOT_X, y + EvoBankAtmMenu.CARD_SLOT_Y, ClientBankAtmData.cardValid ? GREEN : GREEN_DARK);
        drawSlotFrame(graphics, x + EvoBankAtmMenu.DEPOSIT_SLOT_X, y + EvoBankAtmMenu.DEPOSIT_SLOT_Y, GREEN_DARK);

        graphics.drawString(font, "Owner", x + 130, y + 27, MUTED, false);
        graphics.drawString(font, ClientBankAtmData.authenticated ? ClientBankAtmData.ownerName : "Locked", x + 171, y + 27, TEXT, false);
        graphics.drawString(font, "Balance", x + 130, y + 43, MUTED, false);
        graphics.drawString(font, ClientBankAtmData.authenticated ? EvoBankManager.formatAmount(ClientBankAtmData.balance) + " Evo Cash" : "Enter PIN", x + 171, y + 43, ClientBankAtmData.authenticated ? GREEN : MUTED, false);

        if (!ClientBankAtmData.message.isBlank()) {
            graphics.drawString(font, ClientBankAtmData.message, x + 123, y + 112, ClientBankAtmData.positive ? GREEN : ERROR, false);
        } else if (!ClientBankAtmData.hasCard) {
            graphics.drawString(font, "Insert a card to begin.", x + 123, y + 112, MUTED, false);
        }

        if (ClientBankAtmData.authenticated) {
            graphics.drawString(font, "Player", x + 124, y + 133, MUTED, false);
            graphics.drawString(font, "Amount", x + 207, y + 133, MUTED, false);
        }

        drawInventoryFrames(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_SOFT);
        graphics.fill(x, y, x + w, y + 1, 0x8831F2A1);
        graphics.drawString(font, label, x + 8, y + 7, 0xFF4BF2A8, false);
    }

    private void drawSlotFrame(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 2, y - 2, x + 20, y + 20, 0xBB03100B);
        graphics.fill(x - 1, y - 1, x + 19, y, color);
        graphics.fill(x - 1, y + 18, x + 19, y + 19, color);
        graphics.fill(x - 1, y - 1, x, y + 19, color);
        graphics.fill(x + 18, y - 1, x + 19, y + 19, color);
    }

    private void drawInventoryFrames(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(graphics, x + EvoBankAtmMenu.INVENTORY_X + col * 18, y + EvoBankAtmMenu.INVENTORY_Y + row * 18, 0x554BF2A8);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(graphics, x + EvoBankAtmMenu.INVENTORY_X + col * 18, y + EvoBankAtmMenu.HOTBAR_Y, 0x774BF2A8);
        }
    }

    private String cardStatus() {
        if (!ClientBankAtmData.hasCard) return "Waiting for card";
        if (!ClientBankAtmData.cardValid) return "Invalid card";
        if (!ClientBankAtmData.hasPin) return "Set your PIN";
        return ClientBankAtmData.authenticated ? "Unlocked" : "PIN required";
    }

    private int statusColor() {
        if (!ClientBankAtmData.hasCard) return MUTED;
        if (!ClientBankAtmData.cardValid) return ERROR;
        return ClientBankAtmData.authenticated ? GREEN : 0xFFFFD166;
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

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
