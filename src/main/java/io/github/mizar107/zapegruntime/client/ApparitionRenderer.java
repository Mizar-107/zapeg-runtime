package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.scene.SceneMath;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** Renders an entity-shaped hallucination without registering or spawning an entity. */
public final class ApparitionRenderer {

    // The generic black figure uses the classic humanoid UV layout, so it must
    // be baked from the zombie layer: the player layer maps left limbs into
    // texture regions that are fully transparent in this asset.
    private static final ResourceLocation FIGURE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/entity/zombie/zombie.png");
    private static HumanoidModel<LivingEntity> figureModel;
    private static HumanoidModel<LivingEntity> ownModelWide;
    private static HumanoidModel<LivingEntity> ownModelSlim;
    private static RenderType figureRenderType;
    private static ResourceLocation ownSkinTexture;
    private static RenderType ownSkinRenderType;

    private ApparitionRenderer() {}

    public static void installModel(EntityModelSet entityModels) {
        figureModel = new HumanoidModel<>(entityModels.bakeLayer(ModelLayers.ZOMBIE));
        ownModelWide = new HumanoidModel<>(entityModels.bakeLayer(ModelLayers.PLAYER));
        ownModelSlim = new HumanoidModel<>(entityModels.bakeLayer(ModelLayers.PLAYER_SLIM));
        figureModel.setAllVisible(true);
        ownModelWide.setAllVisible(true);
        ownModelSlim.setAllVisible(true);
        figureRenderType = RenderType.entityTranslucent(FIGURE_TEXTURE);
        ownSkinTexture = null;
        ownSkinRenderType = null;
    }

    public static boolean ready() {
        return figureModel != null && ownModelWide != null && ownModelSlim != null;
    }

    /**
     * The motion echo is the target's own delayed shape, so it wears the
     * target's skin on a base wide/slim body; every other profile is the
     * generic black figure. Kept pure so the policy is unit-testable.
     */
    public static boolean usesOwnSilhouette(SceneProfile profile) {
        return profile == SceneProfile.MOTION_ECHO_01;
    }

    /**
     * Every humanoid figure carries the ember-orange eyes — including the
     * motion echo, whose eyes are the tell that the copy is wrong. Kept pure
     * so the policy is unit-testable.
     */
    public static boolean hasGlowingEyes(SceneProfile profile) {
        return profile.rendersFigure();
    }

    private record FigureVisual(HumanoidModel<LivingEntity> model, RenderType renderType) {}

    /**
     * Where the eye-glow pass should land for a profile's main body pass:
     * same offset and scale, with the alpha the eyes should peak at. Null
     * means the profile is too faded to show eyes this frame.
     */
    private record EyePass(
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleX,
            float scaleY,
            float scaleZ,
            float alpha) {}

    private static FigureVisual selectVisual(SceneProfile profile) {
        if (usesOwnSilhouette(profile)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                ResourceLocation skin = minecraft.player.getSkinTextureLocation();
                HumanoidModel<LivingEntity> ownModel =
                        "slim".equals(minecraft.player.getModelName())
                                ? ownModelSlim
                                : ownModelWide;
                if (skin != null && ownModel != null) {
                    return new FigureVisual(ownModel, ownSkinRenderType(skin));
                }
            }
        }
        return figureModel == null || figureRenderType == null
                ? null
                : new FigureVisual(figureModel, figureRenderType);
    }

    private static RenderType ownSkinRenderType(ResourceLocation skin) {
        if (!skin.equals(ownSkinTexture) || ownSkinRenderType == null) {
            ownSkinTexture = skin;
            ownSkinRenderType = RenderType.entityTranslucent(skin);
        }
        return ownSkinRenderType;
    }

    public static void render(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event) {
        SceneProfile profile = snapshot.descriptor().profile();
        if (!profile.rendersFigure()) {
            return;
        }
        FigureVisual visual = selectVisual(profile);
        if (visual == null) {
            return;
        }
        HumanoidModel<LivingEntity> currentModel = visual.model();
        resetPose(currentModel);

        double age = ClientSceneManager.bodyAgeWithPartial(event.getPartialTick());
        float envelope = (float) SceneMath.lifeEnvelope(
                age,
                ClientSceneManager.bodyTtlTicks(),
                9.0D,
                6.0D);
        if (envelope <= 0.001F) {
            return;
        }

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
                .renderBuffers()
                .bufferSource();
        RenderType renderType = visual.renderType();
        EyePass eyes = switch (profile) {
            case ECHO_01 -> renderEcho(snapshot, event, currentModel, buffers, renderType, age, envelope);
            case THRESHOLD_01 -> renderThreshold(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType,
                    age,
                    envelope);
            case MOTION_ECHO_01 -> renderMotionEcho(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType,
                    age,
                    envelope);
            case PERIPHERAL_01 -> renderPeripheral(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType,
                    age,
                    envelope);
            case NEAR_MISS_01 -> renderNearMiss(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType,
                    age,
                    envelope);
            case LIGHT_FAULT_01, FOOTSTEPS_01, SKY_MARK_01, FALSE_PASSAGE_01,
                    CHROMA_BREAK_01, WHISPER_STEPS_01, COLOSSUS_01, VISITATION_01 -> null;
                    // Screen-space / sky / doorway / sound-only profiles render
                    // no humanoid figure; the colossus draws its own eyes, and
                    // the visitation renders nothing in the world at all.
        };
        buffers.endBatch(renderType);
        if (eyes != null && hasGlowingEyes(profile)) {
            EyeGlow.render(
                    currentModel,
                    snapshot,
                    event,
                    eyes.offsetX(),
                    eyes.offsetY(),
                    eyes.offsetZ(),
                    eyes.scaleX(),
                    eyes.scaleY(),
                    eyes.scaleZ(),
                    eyes.alpha());
        }
    }

    /**
     * A silhouette that exists only at the edge of vision: its alpha collapses
     * as the camera look vector nears the anchor, and the profile's blink-long
     * gaze dwell then resolves it. It never tracks the camera — it does not
     * know, or does not care, that it is being watched.
     */
    private static EyePass renderPeripheral(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            double age,
            float envelope) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 look = new Vec3(event.getCamera().getLookVector());
        Vec3 toAnchor = snapshot.anchor().add(0.0D, 1.35D, 0.0D).subtract(cameraPosition);
        double length = toAnchor.length();
        if (length < 1.0E-4D) {
            return null;
        }
        double cos = Mth.clamp(look.dot(toAnchor.scale(1.0D / length)), -1.0D, 1.0D);
        double offAxisDegrees = Math.acos(cos) * Mth.RAD_TO_DEG;
        double gazeCone = snapshot.descriptor().profile().gazeAngleDegrees();
        float periphery = (float) SceneMath.smoothstep(
                gazeCone + 1.0D,
                gazeCone + 26.0D,
                offAxisDegrees);
        float alphaScale = envelope
                * periphery
                * (1.0F - snapshot.gazeProgress());
        if (alphaScale <= 0.001F) {
            return null;
        }

        double phase = (snapshot.descriptor().visualSeed() & 0xFFFFL) * 0.00013D;
        float sway = (float) Math.sin(age * 0.19D + phase);
        currentModel.body.xRot = 0.09F + sway * 0.015F;
        currentModel.head.xRot = 0.14F + (float) Math.sin(age * 0.11D + phase) * 0.02F;
        currentModel.head.zRot = (float) Math.sin(age * 0.07D + phase) * 0.03F;
        currentModel.rightArm.xRot = 0.06F;
        currentModel.leftArm.xRot = 0.06F;

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        renderPass(currentModel, snapshot, event, buffers, renderType,
                0.0F, 0.0F, 0.0F,
                0.80F, 1.10F, 0.80F,
                0.012F, 0.014F, 0.020F, 0.62F * alphaScale);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * 0.018F,
                cameraLeft.y() * 0.018F,
                cameraLeft.z() * 0.018F,
                0.80F, 1.10F, 0.80F,
                0.10F, 0.30F, 0.33F, 0.08F * alphaScale);
        // The eyes live at the edge of vision too: same periphery collapse.
        return new EyePass(0.0F, 0.0F, 0.0F, 0.80F, 1.10F, 0.80F, 0.90F * alphaScale);
    }

    private static EyePass renderEcho(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            double age,
            float envelope) {
        double phase = (snapshot.descriptor().visualSeed() & 0xFFFFL) * 0.00017D;
        float pulse = (float) (0.5D + 0.5D * Math.sin(age * 0.57D + phase));
        float jitter = (float) (Math.sin(age * 2.73D + phase) * 0.012D);
        float alphaScale = envelope * (1.0F - snapshot.gazeProgress() * 0.85F);
        if (alphaScale <= 0.001F) {
            return null;
        }
        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float split = 0.026F + pulse * 0.024F;

        // A slow breath keeps the silhouette alive, and the head almost — but
        // not quite — tracks the camera: it watches back without ever moving
        // its body.
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = snapshot.anchor();
        double toCameraX = cameraPosition.x - anchor.x;
        double toCameraZ = cameraPosition.z - anchor.z;
        float watchYaw = (float) (Math.atan2(-toCameraX, toCameraZ) * Mth.RAD_TO_DEG);
        float relativeWatch = Mth.clamp(
                Mth.wrapDegrees(watchYaw - snapshot.yawDegrees()),
                -70.0F,
                70.0F);
        currentModel.body.xRot = (float) Math.sin(age * 0.21D + phase) * 0.022F;
        currentModel.head.yRot = relativeWatch * 0.75F
                + (float) Math.sin(age * 0.33D + phase * 2.0D) * 0.035F;
        currentModel.head.xRot = (float) Math.sin(age * 0.13D + phase) * 0.028F;
        currentModel.head.zRot = (float) Math.sin(age * 0.27D + phase) * 0.030F;

        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * split + jitter,
                cameraLeft.y() * split,
                cameraLeft.z() * split,
                0.84F, 1.14F, 0.84F,
                0.02F, 0.42F, 0.48F, 0.14F * alphaScale);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                -cameraLeft.x() * split - jitter,
                -cameraLeft.y() * split,
                -cameraLeft.z() * split,
                0.84F, 1.14F, 0.84F,
                0.52F, 0.015F, 0.025F, 0.14F * alphaScale);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                jitter * 0.35F,
                0.0F,
                -jitter * 0.2F,
                0.84F, 1.14F, 0.84F,
                0.018F, 0.018F, 0.024F, 0.91F * alphaScale);
        // The eyes sit on the true figure, not on the chromatic split copies.
        return new EyePass(
                jitter * 0.35F, 0.0F, -jitter * 0.2F,
                0.84F, 1.14F, 0.84F,
                0.95F * alphaScale);
    }

    private static EyePass renderThreshold(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            double age,
            float envelope) {
        float side = (snapshot.descriptor().visualSeed() & 1L) == 0L ? 1.0F : -1.0F;
        float easedGaze = (float) SceneMath.smoothstep(0.0D, 1.0D, snapshot.gazeProgress());
        float withdraw = 0.16F + easedGaze * 0.72F;
        float sink = 0.18F + easedGaze * 0.42F;
        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float lateralX = cameraLeft.x() * side * withdraw;
        float lateralY = cameraLeft.y() * side * withdraw;
        float lateralZ = cameraLeft.z() * side * withdraw;
        float fade = (1.0F - easedGaze * 0.82F) * envelope;
        if (fade <= 0.001F) {
            return null;
        }

        currentModel.leftArm.visible = side < 0.0F;
        currentModel.leftLeg.visible = side < 0.0F;
        currentModel.rightArm.visible = side > 0.0F;
        currentModel.rightLeg.visible = side > 0.0F;
        currentModel.body.zRot = side * (0.13F + easedGaze * 0.10F);
        currentModel.head.yRot = -side * 0.36F;
        currentModel.head.zRot = side * 0.08F;
        currentModel.rightArm.xRot = -0.42F;
        currentModel.leftArm.xRot = -0.42F;

        float tremor = (float) Math.sin(age * 1.91D) * (0.009F + easedGaze * 0.020F);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                lateralX + tremor,
                lateralY - sink,
                lateralZ - tremor,
                0.68F, 1.02F, 0.68F,
                0.015F, 0.018F, 0.023F, 0.88F * fade);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                lateralX - cameraLeft.x() * 0.025F,
                lateralY - sink,
                lateralZ - cameraLeft.z() * 0.025F,
                0.68F, 1.02F, 0.68F,
                0.08F, 0.26F, 0.28F, 0.12F * fade);
        // The eyes withdraw with the half-figure, still watching as it sinks.
        return new EyePass(
                lateralX + tremor, lateralY - sink, lateralZ - tremor,
                0.68F, 1.02F, 0.68F,
                0.95F * fade);
    }

    private static EyePass renderMotionEcho(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            double age,
            float envelope) {
        float stride = (float) Math.sin(age * 0.43D) * 0.72F;
        currentModel.rightArm.xRot = stride;
        currentModel.leftArm.xRot = -stride;
        currentModel.rightLeg.xRot = -stride;
        currentModel.leftLeg.xRot = stride;
        currentModel.head.yRot = (float) Math.sin(age * 0.17D) * 0.09F;

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float fade = (1.0F - snapshot.gazeProgress() * 0.72F) * envelope;
        if (fade <= 0.001F) {
            return null;
        }
        // The copy stays recognisably the target: the newest image is almost
        // untinted, and only the older lag copies sink into cold shadow.
        float breathe = 1.0F + (float) Math.sin(age * 0.9D) * 0.015F;
        float[][] copies = {
            {0.16F, 0.20F, 0.26F, 0.18F},
            {0.38F, 0.44F, 0.52F, 0.30F},
            {0.74F, 0.79F, 0.86F, 0.55F},
        };
        for (int copy = 2; copy >= 0; copy--) {
            float lag = copy * 0.075F;
            float[] tint = copies[2 - copy];
            renderPass(currentModel, snapshot, event, buffers, renderType,
                    -cameraLeft.x() * lag,
                    copy * 0.018F,
                    -cameraLeft.z() * lag,
                    0.82F, 1.08F * breathe, 0.82F,
                    tint[0], tint[1], tint[2], tint[3] * fade);
        }
        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * 0.025F,
                0.0F,
                cameraLeft.z() * 0.025F,
                0.82F, 1.08F * breathe, 0.82F,
                0.12F, 0.34F, 0.38F, 0.10F * fade);
        // The newest copy — the one that still looks like you — has your
        // face and Heraldor's eyes. The lag copies stay eyeless.
        return new EyePass(
                0.0F, 0.0F, 0.0F,
                0.82F, 1.08F * breathe, 0.82F,
                0.95F * fade);
    }

    /**
     * The near-miss: a walking silhouette crossing just behind the target.
     * It fades in and out with the crossing progress itself, so it is never
     * fully solid — a walker glimpsed mid-stride, gone before it is studied.
     */
    private static EyePass renderNearMiss(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            double age,
            float envelope) {
        float progress = snapshot.effectProgress();
        float crossingFade = (float) Math.min(
                SceneMath.smoothstep(0.0D, 0.18D, progress),
                1.0D - SceneMath.smoothstep(0.78D, 1.0D, progress));
        float alphaScale = envelope * crossingFade * (1.0F - snapshot.gazeProgress() * 0.9F);
        if (alphaScale <= 0.001F) {
            return null;
        }

        // A brisk, ordinary walk — the wrongness is that it is here at all.
        float stride = (float) Math.sin(age * 0.62D) * 0.85F;
        currentModel.rightArm.xRot = stride * 0.9F;
        currentModel.leftArm.xRot = -stride * 0.9F;
        currentModel.rightLeg.xRot = -stride;
        currentModel.leftLeg.xRot = stride;
        currentModel.body.xRot = 0.05F;
        currentModel.head.xRot = 0.04F;

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        renderPass(currentModel, snapshot, event, buffers, renderType,
                0.0F, 0.0F, 0.0F,
                0.82F, 1.10F, 0.82F,
                0.014F, 0.016F, 0.021F, 0.78F * alphaScale);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * 0.02F,
                cameraLeft.y() * 0.02F,
                cameraLeft.z() * 0.02F,
                0.82F, 1.10F, 0.82F,
                0.10F, 0.28F, 0.31F, 0.09F * alphaScale);
        // A walker glimpsed mid-stride — and its eyes catch the light wrong.
        return new EyePass(0.0F, 0.0F, 0.0F, 0.82F, 1.10F, 0.82F, 0.90F * alphaScale);
    }

    private static void renderPass(
            HumanoidModel<LivingEntity> currentModel,
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleX,
            float scaleY,
            float scaleZ,
            float red,
            float green,
            float blue,
            float alpha) {
        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = snapshot.anchor();
        pose.pushPose();
        pose.translate(
                anchor.x - cameraPosition.x + offsetX,
                anchor.y - cameraPosition.y + offsetY,
                anchor.z - cameraPosition.z + offsetZ);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - snapshot.yawDegrees()));
        pose.scale(-scaleX, -scaleY, scaleZ);
        pose.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        currentModel.renderToBuffer(
                pose,
                consumer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                red,
                green,
                blue,
                alpha);
        pose.popPose();
    }

    private static void resetPose(HumanoidModel<LivingEntity> currentModel) {
        currentModel.head.resetPose();
        currentModel.hat.resetPose();
        currentModel.body.resetPose();
        currentModel.rightArm.resetPose();
        currentModel.leftArm.resetPose();
        currentModel.rightLeg.resetPose();
        currentModel.leftLeg.resetPose();
        currentModel.setAllVisible(true);
        currentModel.hat.visible = false;
        currentModel.crouching = false;
        currentModel.riding = false;
        currentModel.young = false;
        currentModel.attackTime = 0.0F;
    }
}
