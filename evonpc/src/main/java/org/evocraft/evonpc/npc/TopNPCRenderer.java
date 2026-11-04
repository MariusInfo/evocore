package org.evocraft.evonpc.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class TopNPCRenderer extends EntityRenderer<TopNPC> {

    public TopNPCRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TopNPC entity) {
        return null;
    }

    @Override
    public void render(TopNPC entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Hide vanilla nametag
        // Custom 3D holographic rendering
        String name = entity.getPlayerName();
        String val = entity.getDisplayValue();
        String cat = entity.getCategory();
        int rank = entity.getRank();
        String uuidStr = entity.getUUIDStr();

        if (name == null || name.equals("Coming soon...") || name.equals("Nobody") || uuidStr == null || uuidStr.isEmpty()) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        renderHologram(poseStack, buffer, packedLight, cat, rank, name, val);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderHologram(PoseStack poseStack, MultiBufferSource buffer, int packedLight, String cat, int rank, String name, String val) {
        Font font = Minecraft.getInstance().font;

        poseStack.pushPose();
        poseStack.translate(0.0D, 2.3D, 0.0D);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix4f = poseStack.last().pose();
        int bgColor = (int)(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;

        String color = switch (rank) {
            case 1 -> "§6§l#1 ";
            case 2 -> "§f§l#2 ";
            case 3 -> "§c§l#3 ";
            default -> "§e#" + rank + " ";
        };

        String title = color + "TOP " + displayCategory(cat).toUpperCase();
        String nameStr = "§e" + name;
        String valStr = "§a" + val;

        float x1 = (float)(-font.width(title) / 2);
        font.drawInBatch(title, x1, -24, 0xFFFFFF, false, matrix4f, buffer, Font.DisplayMode.NORMAL, bgColor, packedLight);

        float x2 = (float)(-font.width(nameStr) / 2);
        font.drawInBatch(nameStr, x2, -10, 0xFFFFFF, false, matrix4f, buffer, Font.DisplayMode.NORMAL, bgColor, packedLight);

        float x3 = (float)(-font.width(valStr) / 2);
        font.drawInBatch(valStr, x3, 4, 0xFFFFFF, false, matrix4f, buffer, Font.DisplayMode.NORMAL, bgColor, packedLight);

        poseStack.popPose();
    }

    private String displayCategory(String category) {
        return switch (category) {
            case "money", "bani" -> "Money";
            case "hours", "ore" -> "Hours";
            case "deaths", "decese" -> "Deaths";
            case "claims" -> "Protections";
            case "plots" -> "Plots";
            case "kills" -> "Kills";
            default -> capitalize(category);
        };
    }

    private String capitalize(String str) {
        if(str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
