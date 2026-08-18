package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.mizar107.zapegruntime.scene.SceneMath;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The impossible sky mark: a pale second moon — or, on some seeds, two
 * distant eyes — rendered only on the target's client. Pure additive color
 * quads with no texture and no depth write, so it never touches the world,
 * the framebuffer, or a shader pack's own sky: real terrain still occludes
 * it through the depth test, and clouds still drift across it.
 */
public final class SkyMarkRenderer {

    private SkyMarkRenderer() {}

    public static void render(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event) {
        double age = ClientSceneManager.bodyAgeWithPartial(event.getPartialTick());
        float envelope = (float) SceneMath.lifeEnvelope(
                age,
                ClientSceneManager.bodyTtlTicks(),
                30.0D,
                30.0D);
        // A held direct look makes the mark thin out as it resolves.
        float strength = envelope * (1.0F - snapshot.gazeProgress() * 0.9F);
        if (strength <= 0.001F) {
            return;
        }

        long seed = snapshot.descriptor().visualSeed();
        boolean eyes = (seed & 2L) != 0L;
        double phase = (seed & 0xFFFFL) * 0.00011D;
        // A slow breath, never a pulse: the mark is patient.
        float breathe = 0.9F + 0.1F * (float) Math.sin(age * 0.05D + phase);

        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 center = snapshot.anchor();

        pose.pushPose();
        pose.translate(
                center.x - cameraPosition.x,
                center.y - cameraPosition.y,
                center.z - cameraPosition.z);
        // Billboard exactly like a name tag: the local XY plane faces the
        // camera, so the mark is always seen full-on.
        pose.mulPose(event.getCamera().rotation());
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float size = 26.0F * breathe;
        // Outer halo: a wide, almost-nothing cold glow.
        quad(buffer, matrix, 0.0F, 0.0F, size * 2.4F, 0.05F * strength, 0.36F, 0.44F, 0.52F);
        // The disc: two quads rotated 45° apart read as a soft circle.
        quad(buffer, matrix, 0.0F, 0.0F, 0.0F, size, size, 0.34F * strength, 0.62F, 0.68F, 0.75F);
        quad(buffer, matrix, 0.0F, 0.0F, 45.0F, size, size, 0.34F * strength, 0.62F, 0.68F, 0.75F);

        if (eyes) {
            // Two level slits, too far apart and too still to be a face.
            float eyeY = size * 0.16F;
            float eyeSpread = size * 0.42F;
            float eyeW = size * 0.34F;
            float eyeH = size * 0.055F;
            quad(buffer, matrix, -eyeSpread, eyeY, 0.0F, eyeW, eyeH, 0.55F * strength, 0.72F, 0.06F, 0.07F);
            quad(buffer, matrix, eyeSpread, eyeY, 0.0F, eyeW, eyeH, 0.55F * strength, 0.72F, 0.06F, 0.07F);
        }

        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** An axis-aligned quad in the billboard plane, centred on (cx, cy). */
    private static void quad(
            BufferBuilder buffer,
            Matrix4f matrix,
            float cx,
            float cy,
            float width,
            float alpha,
            float red,
            float green,
            float blue) {
        quad(buffer, matrix, cx, cy, 0.0F, width, width, alpha, red, green, blue);
    }

    /** A quad in the billboard plane, centred on (cx, cy), rotated in-plane. */
    private static void quad(
            BufferBuilder buffer,
            Matrix4f matrix,
            float cx,
            float cy,
            float rotationDegrees,
            float width,
            float height,
            float alpha,
            float red,
            float green,
            float blue) {
        double rotation = Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        float halfW = width * 0.5F;
        float halfH = height * 0.5F;
        float[][] corners = {
            {-halfW, -halfH},
            {-halfW, halfH},
            {halfW, halfH},
            {halfW, -halfH},
        };
        for (float[] corner : corners) {
            float x = corner[0] * cos - corner[1] * sin;
            float y = corner[0] * sin + corner[1] * cos;
            buffer.vertex(matrix, cx + x, cy + y, 0.0F)
                    .color(red, green, blue, alpha)
                    .endVertex();
        }
    }
}
