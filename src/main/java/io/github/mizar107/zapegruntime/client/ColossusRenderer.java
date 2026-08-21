package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.scene.ColossusChoreography;
import io.github.mizar107.zapegruntime.scene.SceneMath;
import io.github.mizar107.zapegruntime.scene.ScenePalette;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The far colossus: a ~100-block humanoid silhouette rendered as pure color
 * boxes — no entity, no hitbox, no AI, no loot, nothing to fight or farm.
 *
 * <p>Fog is the whole aesthetic. The position-color shader applies no fog of
 * its own, so the silhouette is mixed toward the client's fog color by hand:
 * the honest distance fog fraction scaled by the stage's wrongness factor.
 * Far stages dissolve into the fog; near stages stay darker than honest fog
 * would allow, so the figure faintly reads <em>through</em> it. Under shader
 * packs (Oculus/Embeddium) the fog uniforms come from the pack's last frame,
 * so the mix can drift from the pack's real fog curve — the figure may look
 * slightly more or less present than vanilla terrain at the same distance.
 * That drift reads as wrongness, which is the point.
 *
 * <p>Depth testing stays on, so real (loaded) terrain occludes the figure;
 * beyond the loaded world there is nothing to occlude it, which is correct:
 * out there it is the horizon.
 *
 * <p>The eyes are the signature: two ember-orange glows drawn in a second,
 * additive pass. Additive position-color quads are the textureless twin of
 * vanilla's {@code RenderType.eyes} — unfogged, unlit, steady — so they punch
 * through darkness and fog at 280 blocks and are the last thing visible as
 * the body fades. They sit slightly too far apart, and during the finale's
 * held watch they slowly narrow.
 */
public final class ColossusRenderer {

    private ColossusRenderer() {}

    public static void render(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event) {
        int stage = snapshot.descriptor().stage();
        double age = ClientSceneManager.bodyAgeWithPartial(event.getPartialTick());
        float envelope = (float) SceneMath.lifeEnvelope(
                age,
                ClientSceneManager.bodyTtlTicks(),
                24.0D,
                26.0D);
        if (envelope <= 0.001F) {
            return;
        }
        int vanishTick = ColossusChoreography.vanishTick(stage);
        if (vanishTick >= 0 && age >= vanishTick) {
            // The finale's resolve: after the held watch it is simply gone.
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = snapshot.anchor();
        double distance = cameraPosition.distanceTo(anchor);

        float[] fogColor = RenderSystem.getShaderFogColor();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        double fogSpan = Math.max(1.0D, (double) fogEnd - fogStart);
        double honestFog = Math.max(0.0D, Math.min(1.0D, (distance - fogStart) / fogSpan));
        double[] rgb = ColossusChoreography.foggedColor(
                0.010D,
                0.012D,
                0.018D,
                fogColor,
                honestFog * ColossusChoreography.fogStrength(stage));

        long seed = snapshot.descriptor().visualSeed();
        double phase = (seed & 0xFFFFL) * 0.00011D;
        // Breathing: the whole figure swells and settles, very slowly.
        double breathe = 1.0D + Math.sin(age * 0.14D + phase) * 0.015D;
        float alpha = (float) (ColossusChoreography.baseAlpha(stage) * envelope);
        float rock = (float) ColossusChoreography.stepRockDegrees(stage, age, seed);
        // The far-plane clamp factor computed with the anchor in
        // observeColossus (1 when the authored distance already fits this
        // client's far plane): body and eyes shrink together, so the
        // silhouette keeps its authored angular size.
        float approach = snapshot.effectProgress() > 0.0F ? snapshot.effectProgress() : 1.0F;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(
                anchor.x - cameraPosition.x,
                anchor.y - cameraPosition.y,
                anchor.z - cameraPosition.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - snapshot.yawDegrees()));
        pose.mulPose(Axis.ZP.rotationDegrees(rock));
        pose.scale(
                (float) breathe * approach,
                (float) breathe * approach,
                (float) breathe * approach);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float red = (float) rgb[0];
        float green = (float) rgb[1];
        float blue = (float) rgb[2];
        // A simple huge humanoid reads better at distance than fine detail:
        // two legs, a torso, hanging arms, a head — 96 blocks in all, and
        // deliberately thin like the reference art: a towering narrow shape,
        // not a broad one.
        box(buffer, matrix, -8.5F, 0.0F, -4.5F, 7.0F, 46.0F, 9.0F, red, green, blue, alpha);
        box(buffer, matrix, 1.5F, 0.0F, -4.5F, 7.0F, 46.0F, 9.0F, red, green, blue, alpha);
        box(buffer, matrix, -10.0F, 46.0F, -5.5F, 20.0F, 36.0F, 11.0F, red, green, blue, alpha);
        box(buffer, matrix, -15.0F, 44.0F, -3.5F, 5.0F, 36.0F, 7.0F, red, green, blue, alpha);
        box(buffer, matrix, 10.0F, 44.0F, -3.5F, 5.0F, 36.0F, 7.0F, red, green, blue, alpha);
        box(buffer, matrix, -6.5F, 82.0F, -6.5F, 13.0F, 14.0F, 13.0F, red, green, blue, alpha);

        tesselator.end();

        // The eyes: additive, unfogged and steady, so they read at every
        // stage distance and outlast the body's fade. They belong to the
        // face — they dim as the camera leaves the front hemisphere instead
        // of shining through the back of the head.
        double toCameraX = cameraPosition.x - anchor.x;
        double toCameraZ = cameraPosition.z - anchor.z;
        double horizontal = Math.hypot(toCameraX, toCameraZ);
        if (horizontal >= 1.0E-4D) {
            double yaw = Math.toRadians(snapshot.yawDegrees());
            double cosine = ((-Math.sin(yaw)) * toCameraX + Math.cos(yaw) * toCameraZ)
                    / horizontal;
            float eyeStrength = (float) (ScenePalette.eyeHold(envelope)
                    * ScenePalette.frontality(cosine));
            if (eyeStrength > 0.001F) {
                float narrow = (float) ColossusChoreography.eyeNarrow(stage, age);
                float eyeZ = (float) (ColossusChoreography.EYE_FACE_Z - 0.06D);
                RenderSystem.blendFunc(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE);
                buffer = tesselator.getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (float side : new float[] {-1.0F, 1.0F}) {
                    float cx = side * (float) ColossusChoreography.EYE_HALF_SPACING;
                    eye(buffer, matrix, cx, (float) ColossusChoreography.EYE_CENTER_Y, eyeZ,
                            (float) ColossusChoreography.EYE_WIDTH,
                            (float) ColossusChoreography.EYE_HEIGHT * narrow,
                            eyeStrength,
                            ScenePalette.EYE_RED, ScenePalette.EYE_GREEN,
                            ScenePalette.EYE_BLUE);
                    eye(buffer, matrix, cx, (float) ColossusChoreography.EYE_CENTER_Y, eyeZ,
                            (float) ColossusChoreography.EYE_WIDTH
                                    * ScenePalette.EYE_HALO_SIZE_SCALE,
                            (float) ColossusChoreography.EYE_HEIGHT * narrow
                                    * ScenePalette.EYE_HALO_SIZE_SCALE,
                            eyeStrength * ScenePalette.EYE_HALO_ALPHA_SCALE,
                            ScenePalette.EYE_RED, ScenePalette.EYE_GREEN,
                            ScenePalette.EYE_BLUE);
                }
                tesselator.end();
                RenderSystem.defaultBlendFunc();
            }
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** A vertical quad on the face plane, centred on (cx, cy). */
    private static void eye(
            BufferBuilder buffer,
            Matrix4f matrix,
            float cx,
            float cy,
            float z,
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
        quad(buffer, matrix, x1, y1, z, x2, y1, z, x2, y2, z, x1, y2, z,
                red, green, blue, alpha);
    }

    /** A closed axis-aligned box from (x, y, z) to (x+w, y+h, z+d). */
    private static void box(
            BufferBuilder buffer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth,
            float red,
            float green,
            float blue,
            float alpha) {
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;
        // Culling is disabled, so winding is irrelevant; emit all six faces.
        quad(buffer, matrix, x, y, z, x2, y, z, x2, y2, z, x, y2, z, red, green, blue, alpha);
        quad(buffer, matrix, x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, red, green, blue, alpha);
        quad(buffer, matrix, x, y, z, x, y, z2, x, y2, z2, x, y2, z, red, green, blue, alpha);
        quad(buffer, matrix, x2, y, z, x2, y, z2, x2, y2, z2, x2, y2, z, red, green, blue, alpha);
        quad(buffer, matrix, x, y2, z, x2, y2, z, x2, y2, z2, x, y2, z2, red, green, blue, alpha);
        quad(buffer, matrix, x, y, z, x2, y, z, x2, y, z2, x, y, z2, red, green, blue, alpha);
    }

    private static void quad(
            BufferBuilder buffer,
            Matrix4f matrix,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            float red,
            float green,
            float blue,
            float alpha) {
        buffer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x3, y3, z3).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x4, y4, z4).color(red, green, blue, alpha).endVertex();
    }
}
