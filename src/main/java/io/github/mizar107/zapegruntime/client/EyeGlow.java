package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.scene.ScenePalette;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The ember-orange eyes, shared by every humanoid apparition. The technique
 * mirrors vanilla's {@code RenderType.eyes} (spider/enderman glowing eyes) —
 * additive blending, no fog, no lightmap — but as textureless position-color
 * quads, so no asset is needed and the exact placement, spacing and size are
 * ours. A soft oversized halo quad sits behind each bright core.
 *
 * <p>The quads ride the head part's pose, so they follow whatever watching,
 * tilting or walking the profile already animated. They fade out as the
 * camera leaves the figure's front hemisphere rather than shining through
 * the back of the head.
 */
final class EyeGlow {

    // Head-local placement in blocks: the head cube spans x -0.25..0.25,
    // y -0.5..0 above the neck pivot, and its face is the -z side. The eyes
    // sit where a skin's eyes would be, very slightly too wide and too level.
    private static final float EYE_X = 0.13F;
    private static final float EYE_Y = -0.28F;
    private static final float EYE_Z = -0.253F;
    private static final float EYE_WIDTH = 0.15F;
    private static final float EYE_HEIGHT = 0.085F;

    private EyeGlow() {}

    static void render(
            HumanoidModel<LivingEntity> model,
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event,
            float offsetX,
            float offsetY,
            float offsetZ,
            float scaleX,
            float scaleY,
            float scaleZ,
            float alpha) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = snapshot.anchor();
        double toCameraX = cameraPosition.x - anchor.x;
        double toCameraZ = cameraPosition.z - anchor.z;
        double horizontal = Math.hypot(toCameraX, toCameraZ);
        if (horizontal < 1.0E-4D) {
            return;
        }
        double yaw = Math.toRadians(snapshot.yawDegrees());
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        double cosine = (facingX * toCameraX + facingZ * toCameraZ) / horizontal;
        float strength = (float) (alpha * ScenePalette.frontality(cosine));
        if (strength <= 0.001F) {
            return;
        }

        // The same pose chain as the body pass, then the head part's own
        // pivot and rotation, so the eyes live exactly on the animated face.
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(
                anchor.x - cameraPosition.x + offsetX,
                anchor.y - cameraPosition.y + offsetY,
                anchor.z - cameraPosition.z + offsetZ);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - snapshot.yawDegrees()));
        pose.scale(-scaleX, -scaleY, scaleZ);
        pose.translate(0.0F, -1.501F, 0.0F);
        pose.translate(
                model.head.x / 16.0F, model.head.y / 16.0F, model.head.z / 16.0F);
        pose.mulPose(new Quaternionf()
                .rotationZYX(model.head.zRot, model.head.yRot, model.head.xRot));
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float side : new float[] {-1.0F, 1.0F}) {
            float cx = side * EYE_X;
            quad(buffer, matrix, cx, EYE_Y, EYE_WIDTH, EYE_HEIGHT,
                    strength,
                    ScenePalette.EYE_RED, ScenePalette.EYE_GREEN, ScenePalette.EYE_BLUE);
            quad(buffer, matrix, cx, EYE_Y,
                    EYE_WIDTH * ScenePalette.EYE_HALO_SIZE_SCALE,
                    EYE_HEIGHT * ScenePalette.EYE_HALO_SIZE_SCALE,
                    strength * ScenePalette.EYE_HALO_ALPHA_SCALE,
                    ScenePalette.EYE_RED, ScenePalette.EYE_GREEN, ScenePalette.EYE_BLUE);
        }
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** A quad parallel to the face plane, centred on (cx, cy). */
    private static void quad(
            BufferBuilder buffer,
            Matrix4f matrix,
            float cx,
            float cy,
            float width,
            float height,
            float alpha,
            float red,
            float green,
            float blue) {
        float x1 = cx - width * 0.5F;
        float x2 = cx + width * 0.5F;
        float y1 = cy - height * 0.5F;
        float y2 = cy + height * 0.5F;
        buffer.vertex(matrix, x1, y1, EYE_Z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x2, y1, EYE_Z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x2, y2, EYE_Z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x1, y2, EYE_Z).color(red, green, blue, alpha).endVertex();
    }
}
