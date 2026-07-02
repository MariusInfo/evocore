package org.evocraft.evocore.npc;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TopNPCRenderer extends LivingEntityRenderer<TopNPC, PlayerModel<TopNPC>> {

    private static final Map<String, GameProfile> PROFILE_CACHE = new HashMap<>();

    public TopNPCRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(TopNPC entity) {
        String name = entity.getPlayerName();
        String uuidStr = entity.getUUIDStr();

        if (name == null || name.equals("În curând...") || name.equals("Nimeni") || uuidStr == null || uuidStr.isEmpty()) {
            return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
        }

        GameProfile profile = PROFILE_CACHE.computeIfAbsent(name, k -> {
            try {
                // Acum îi dăm UUID-ul REAL din baza de date, așa că QuickSkin are fix ce-i trebuie!
                return new GameProfile(UUID.fromString(uuidStr), name);
            } catch (Exception e) {
                return new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()), name);
            }
        });

        return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile);
    }

    @Override
    protected void renderNameTag(TopNPC entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Ascundem Vanilla nametag-ul (entity.evocore.top_npc)
    }

    @Override
    public void render(TopNPC entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        double dist = this.entityRenderDispatcher.distanceToSqr(entity);
        if (dist <= 4096.0D) {

            String title = "§e§lLocul " + entity.getRank() + " - " + capitalize(entity.getCategory());
            String nameStr = "§f" + entity.getPlayerName();
            String valStr = "§a" + entity.getDisplayValue();

            float height = entity.getBbHeight() + 0.9F;

            poseStack.pushPose();
            poseStack.translate(0.0D, height, 0.0D);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix4f = poseStack.last().pose();

            float bgOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            int bgColor = (int)(bgOpacity * 255.0F) << 24;
            net.minecraft.client.gui.Font font = this.getFont();

            float x1 = (float)(-font.width(title) / 2);
            font.drawInBatch(title, x1, -24, 0xFFFFFF, false, matrix4f, buffer, net.minecraft.client.gui.Font.DisplayMode.NORMAL, bgColor, packedLight);

            float x2 = (float)(-font.width(nameStr) / 2);
            font.drawInBatch(nameStr, x2, -10, 0xFFFFFF, false, matrix4f, buffer, net.minecraft.client.gui.Font.DisplayMode.NORMAL, bgColor, packedLight);

            float x3 = (float)(-font.width(valStr) / 2);
            font.drawInBatch(valStr, x3, 4, 0xFFFFFF, false, matrix4f, buffer, net.minecraft.client.gui.Font.DisplayMode.NORMAL, bgColor, packedLight);

            poseStack.popPose();
        }
    }

    private String capitalize(String str) {
        if(str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}