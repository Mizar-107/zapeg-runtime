package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/** Renders an entity-shaped hallucination without registering or spawning an entity. */
public final class ApparitionRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/entity/zombie/zombie.png");
    private static HumanoidModel<LivingEntity> model;

    private ApparitionRenderer() {}

    public static void installModel(EntityModelSet entityModels) {
        model = new HumanoidModel<>(entityModels.bakeLayer(ModelLayers.PLAYER));
        model.setAllVisible(true);
    }

    public static boolean ready() {
        return model != null;
    }

    public static void render(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event) {
        HumanoidModel<LivingEntity> currentModel = model;
        if (currentModel == null || !snapshot.descriptor().profile().rendersFigure()) {
            return;
        }
        resetPose(currentModel);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
                .renderBuffers()
                .bufferSource();
        RenderType renderType = RenderType.entityTranslucent(TEXTURE);
        switch (snapshot.descriptor().profile()) {
            case ECHO_01 -> renderEcho(snapshot, event, currentModel, buffers, renderType);
            case THRESHOLD_01 -> renderThreshold(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType);
            case MOTION_ECHO_01 -> renderMotionEcho(
                    snapshot,
                    event,
                    currentModel,
                    buffers,
                    renderType);
            case LIGHT_FAULT_01 -> {
                // This profile is intentionally screen-space only.
            }
        }
        buffers.endBatch(renderType);
    }

    private static void renderEcho(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType) {
        double age = ClientSceneManager.ageWithPartial(event.getPartialTick());
        double phase = (snapshot.descriptor().visualSeed() & 0xFFFFL) * 0.00017D;
        float pulse = (float) (0.5D + 0.5D * Math.sin(age * 0.57D + phase));
        float jitter = (float) (Math.sin(age * 2.73D + phase) * 0.012D);
        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float split = 0.026F + pulse * 0.024F;

        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * split + jitter,
                cameraLeft.y() * split,
                cameraLeft.z() * split,
                0.84F, 1.14F, 0.84F,
                0.02F, 0.42F, 0.48F, 0.14F);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                -cameraLeft.x() * split - jitter,
                -cameraLeft.y() * split,
                -cameraLeft.z() * split,
                0.84F, 1.14F, 0.84F,
                0.52F, 0.015F, 0.025F, 0.14F);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                jitter * 0.35F,
                0.0F,
                -jitter * 0.2F,
                0.84F, 1.14F, 0.84F,
                0.018F, 0.018F, 0.024F, 0.91F);
    }

    private static void renderThreshold(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType) {
        double age = ClientSceneManager.ageWithPartial(event.getPartialTick());
        float side = (snapshot.descriptor().visualSeed() & 1L) == 0L ? 1.0F : -1.0F;
        float withdraw = 0.16F + snapshot.gazeProgress() * 0.72F;
        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float lateralX = cameraLeft.x() * side * withdraw;
        float lateralY = cameraLeft.y() * side * withdraw;
        float lateralZ = cameraLeft.z() * side * withdraw;
        float fade = 1.0F - snapshot.gazeProgress() * 0.82F;

        currentModel.leftArm.visible = side < 0.0F;
        currentModel.leftLeg.visible = side < 0.0F;
        currentModel.rightArm.visible = side > 0.0F;
        currentModel.rightLeg.visible = side > 0.0F;
        currentModel.body.zRot = side * 0.13F;
        currentModel.head.yRot = -side * 0.36F;
        currentModel.head.zRot = side * 0.08F;
        currentModel.rightArm.xRot = -0.42F;
        currentModel.leftArm.xRot = -0.42F;

        float tremor = (float) Math.sin(age * 1.91D) * 0.009F;
        renderPass(currentModel, snapshot, event, buffers, renderType,
                lateralX + tremor,
                lateralY - 0.18F,
                lateralZ - tremor,
                0.68F, 1.02F, 0.68F,
                0.015F, 0.018F, 0.023F, 0.88F * fade);
        renderPass(currentModel, snapshot, event, buffers, renderType,
                lateralX - cameraLeft.x() * 0.025F,
                lateralY - 0.18F,
                lateralZ - cameraLeft.z() * 0.025F,
                0.68F, 1.02F, 0.68F,
                0.08F, 0.26F, 0.28F, 0.12F * fade);
    }

    private static void renderMotionEcho(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            HumanoidModel<LivingEntity> currentModel,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType) {
        double age = ClientSceneManager.ageWithPartial(event.getPartialTick());
        float stride = (float) Math.sin(age * 0.43D) * 0.72F;
        currentModel.rightArm.xRot = stride;
        currentModel.leftArm.xRot = -stride;
        currentModel.rightLeg.xRot = -stride;
        currentModel.leftLeg.xRot = stride;
        currentModel.head.yRot = (float) Math.sin(age * 0.17D) * 0.09F;

        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float fade = 1.0F - snapshot.gazeProgress() * 0.72F;
        for (int copy = 2; copy >= 0; copy--) {
            float lag = copy * 0.075F;
            float alpha = (0.13F + (2 - copy) * 0.12F) * fade;
            renderPass(currentModel, snapshot, event, buffers, renderType,
                    -cameraLeft.x() * lag,
                    copy * 0.018F,
                    -cameraLeft.z() * lag,
                    0.82F, 1.08F, 0.82F,
                    0.055F, 0.075F, 0.095F, alpha);
        }
        renderPass(currentModel, snapshot, event, buffers, renderType,
                cameraLeft.x() * 0.025F,
                0.0F,
                cameraLeft.z() * 0.025F,
                0.82F, 1.08F, 0.82F,
                0.12F, 0.34F, 0.38F, 0.10F * fade);
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
