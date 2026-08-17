package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
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
    private static PlayerModel<LivingEntity> model;

    private ApparitionRenderer() {}

    public static void installModel(EntityModelSet entityModels) {
        model = new PlayerModel<>(entityModels.bakeLayer(ModelLayers.PLAYER), false);
        model.setAllVisible(true);
    }

    public static boolean ready() {
        return model != null;
    }

    public static void render(SceneDescriptor descriptor, RenderLevelStageEvent event) {
        PlayerModel<LivingEntity> currentModel = model;
        if (currentModel == null) {
            return;
        }
        resetPose(currentModel);

        double age = ClientSceneManager.ageWithPartial(event.getPartialTick());
        double phase = (descriptor.visualSeed() & 0xFFFFL) * 0.00017D;
        float pulse = (float) (0.5D + 0.5D * Math.sin(age * 0.57D + phase));
        float jitter = (float) (Math.sin(age * 2.73D + phase) * 0.012D);
        Vector3f cameraLeft = event.getCamera().getLeftVector();
        float split = 0.026F + pulse * 0.024F;

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance()
                .renderBuffers()
                .bufferSource();
        RenderType renderType = RenderType.entityTranslucent(TEXTURE);

        renderPass(
                currentModel,
                descriptor,
                event,
                buffers,
                renderType,
                cameraLeft.x() * split + jitter,
                cameraLeft.y() * split,
                cameraLeft.z() * split,
                0.02F,
                0.42F,
                0.48F,
                0.14F);
        renderPass(
                currentModel,
                descriptor,
                event,
                buffers,
                renderType,
                -cameraLeft.x() * split - jitter,
                -cameraLeft.y() * split,
                -cameraLeft.z() * split,
                0.52F,
                0.015F,
                0.025F,
                0.14F);
        renderPass(
                currentModel,
                descriptor,
                event,
                buffers,
                renderType,
                jitter * 0.35F,
                0.0F,
                -jitter * 0.2F,
                0.018F,
                0.018F,
                0.024F,
                0.91F);
        buffers.endBatch(renderType);
    }

    private static void renderPass(
            PlayerModel<LivingEntity> currentModel,
            SceneDescriptor descriptor,
            RenderLevelStageEvent event,
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            float offsetX,
            float offsetY,
            float offsetZ,
            float red,
            float green,
            float blue,
            float alpha) {
        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = descriptor.anchor();
        pose.pushPose();
        pose.translate(
                anchor.x - cameraPosition.x + offsetX,
                anchor.y - cameraPosition.y + offsetY,
                anchor.z - cameraPosition.z + offsetZ);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - descriptor.yawDegrees()));
        pose.scale(-0.84F, -1.14F, 0.84F);
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

    private static void resetPose(PlayerModel<LivingEntity> currentModel) {
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
