package io.github.mizar107.zapegruntime.boss.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormBoss;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.VisualState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Bounded full-bright cracks and weak-point lamps; never a whole-model glow pass. */
final class NinthFormEmissiveLayer extends RenderLayer<NinthFormBoss, NinthFormModel> {

    private final NinthFormModel glowModel;

    NinthFormEmissiveLayer(
            RenderLayerParent<NinthFormBoss, NinthFormModel> parent,
            EntityRendererProvider.Context context) {
        super(parent);
        glowModel = new NinthFormModel(context.bakeLayer(NinthFormModel.LAYER_LOCATION));
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            NinthFormBoss boss,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        VisualState state = NinthFormRenderState.resolve(
                boss.combatPhase(),
                boss.attackId(),
                boss.attackTick(),
                boss.brokenPointMask(),
                ageInTicks);
        if (state.emissiveAlpha() <= 0.0F) {
            return;
        }

        glowModel.setupAnim(
                boss, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        glowModel.configureEmissive(state);
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityTranslucentEmissive(NinthFormRenderer.EMISSIVE_TEXTURE));
        float red = state.phase() == NinthFormPhase.FINAL ? 0.38F : 0.22F;
        float green = state.phase() == NinthFormPhase.FINAL ? 0.68F : 0.82F;
        float blue = state.phase() == NinthFormPhase.FINAL ? 0.96F : 0.78F;
        glowModel.renderToBuffer(
                poseStack,
                consumer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                red,
                green,
                blue,
                state.emissiveAlpha());
    }
}
