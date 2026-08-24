package io.github.mizar107.zapegruntime.boss.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.combat.NinthFormBoss;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.VisualState;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/** Phase-aware renderer for the parent and its five baked multipart silhouettes. */
public final class NinthFormRenderer
        extends LivingEntityRenderer<NinthFormBoss, NinthFormModel> {

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ZapeGRuntime.MOD_ID, "textures/entity/ninth_form.png");
    public static final ResourceLocation EMISSIVE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    ZapeGRuntime.MOD_ID, "textures/entity/ninth_form_emissive.png");

    public NinthFormRenderer(EntityRendererProvider.Context context) {
        super(context, new NinthFormModel(context.bakeLayer(NinthFormModel.LAYER_LOCATION)), 7.0F);
        addLayer(new NinthFormEmissiveLayer(this, context));
    }

    @Override
    public ResourceLocation getTextureLocation(NinthFormBoss boss) {
        return TEXTURE;
    }

    @Override
    @Nullable
    protected RenderType getRenderType(
            NinthFormBoss boss, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (!bodyVisible) {
            if (translucent) {
                return RenderType.entityTranslucent(TEXTURE);
            }
            return glowing ? RenderType.outline(TEXTURE) : null;
        }
        VisualState state = state(boss, 0.0F);
        if (state.renderMode() == NinthFormRenderState.RenderMode.TRANSLUCENT) {
            return RenderType.entityTranslucent(TEXTURE);
        }
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    @Override
    protected void setupRotations(
            NinthFormBoss boss,
            PoseStack poseStack,
            float ageInTicks,
            float rotationYaw,
            float partialTick) {
        super.setupRotations(boss, poseStack, ageInTicks, rotationYaw, partialTick);
        VisualState state = state(boss, partialTick);
        if (state.rollDegrees() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.rollDegrees()));
        }
    }

    @Override
    protected boolean isShaking(NinthFormBoss boss) {
        VisualState state = state(boss, 0.0F);
        return state.phase()
                        == io.github.mizar107.zapegruntime.boss.api.NinthFormPhase.INTERLUDE
                || state.window() == NinthFormRenderState.AttackWindow.ACTIVE;
    }

    private static VisualState state(NinthFormBoss boss, float partialTick) {
        return NinthFormRenderState.resolve(
                boss.combatPhase(),
                boss.attackId(),
                boss.attackTick(),
                boss.brokenPointMask(),
                boss.tickCount + partialTick);
    }
}
