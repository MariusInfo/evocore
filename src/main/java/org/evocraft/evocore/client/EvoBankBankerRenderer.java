package org.evocraft.evocore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.evocraft.evocore.EvoCore;
import org.evocraft.evocore.bank.EvoBankManager;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = EvoCore.MODID, value = Dist.CLIENT)
public class EvoBankBankerRenderer {
    private static final ResourceLocation BANKER_TEXTURE = new ResourceLocation(EvoCore.MODID, "textures/entity/evobank_banker.png");
    private static PlayerModel<Villager> bankerModel;

    @SubscribeEvent
    public static void onRenderBanker(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Villager villager) || !isBanker(villager)) {
            return;
        }

        event.setCanceled(true);
        renderBanker(villager, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        renderName(villager, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    private static boolean isBanker(Villager villager) {
        if (villager.getTags().contains(EvoBankManager.BANKER_TAG)) return true;
        return villager.hasCustomName()
                && (villager.getDisplayName().getString().contains("EvoBank Banker")
                || villager.getDisplayName().getString().contains("Banker"));
    }

    private static void renderBanker(Villager villager, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerModel<Villager> model = getBankerModel();
        float bodyYaw = Mth.rotLerp(partialTick, villager.yBodyRotO, villager.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, villager.yHeadRotO, villager.yHeadRot);
        float headPitch = Mth.lerp(partialTick, villager.xRotO, villager.getXRot());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);

        model.young = villager.isBaby();
        model.riding = false;
        model.crouching = false;
        model.attackTime = 0.0F;
        model.prepareMobModel(villager, 0.0F, 0.0F, partialTick);
        model.setupAnim(villager, 0.0F, 0.0F, villager.tickCount + partialTick, headYaw - bodyYaw, headPitch);

        VertexConsumer vertex = buffer.getBuffer(RenderType.entityCutoutNoCull(BANKER_TEXTURE));
        model.renderToBuffer(poseStack, vertex, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private static void renderName(Villager villager, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!villager.hasCustomName() || !villager.isCustomNameVisible()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String text = villager.getDisplayName().getString();
        float y = villager.getBbHeight() + 0.6F;

        poseStack.pushPose();
        poseStack.translate(0.0D, y, 0.0D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        int background = ((int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F)) << 24;
        font.drawInBatch(text, x, 0.0F, 0xFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, background, packedLight);
        poseStack.popPose();
    }

    private static PlayerModel<Villager> getBankerModel() {
        if (bankerModel == null) {
            bankerModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return bankerModel;
    }
}
