package io.github.mizar107.zapegruntime.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.mizar107.zapegruntime.scene.SceneMath;
import io.github.mizar107.zapegruntime.scene.ScenePalette;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The false passage: a doorway standing where no doorway should be, rendered
 * as pure color quads — no blocks, no collision, no world state. Two jambs,
 * a lintel and a recessed dark interior that hints at a corridor beyond.
 * When the target commits to approaching, the whole thing folds into the
 * ground (the manager drives {@code effectProgress} 0→1) and is gone.
 *
 * <p>This is the deliberately simpler render-only variant from the plan: a
 * flat doorway silhouette with a painted-on interior, not fake walkable
 * geometry. It cannot be entered, so it can never trap or mislead movement.
 */
public final class FalsePassageRenderer {

    private FalsePassageRenderer() {}

    public static void render(
            ClientSceneManager.RenderSnapshot snapshot,
            RenderLevelStageEvent event) {
        double age = ClientSceneManager.bodyAgeWithPartial(event.getPartialTick());
        float envelope = (float) SceneMath.lifeEnvelope(
                age,
                ClientSceneManager.bodyTtlTicks(),
                18.0D,
                10.0D);
        float collapse = snapshot.effectProgress();
        float strength = envelope * (1.0F - collapse);
        if (strength <= 0.001F) {
            return;
        }

        long seed = snapshot.descriptor().visualSeed();
        double phase = (seed & 0xFFFFL) * 0.00013D;
        // The interior darkness breathes very slightly, like a draught.
        float draught = 0.85F + 0.15F * (float) Math.sin(age * 0.09D + phase);

        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 anchor = snapshot.anchor();

        pose.pushPose();
        pose.translate(
                anchor.x - cameraPosition.x,
                anchor.y - cameraPosition.y,
                anchor.z - cameraPosition.z);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - snapshot.yawDegrees()));
        // The fold: as the collapse progresses the doorway sinks into the
        // ground and its edges draw inward.
        pose.translate(0.0F, -2.4D * collapse, 0.0D);
        float pinch = 1.0F - 0.35F * collapse;
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

        float frameAlpha = 0.85F * strength;
        float halfWidth = 0.62F * pinch;
        float height = 2.15F;
        float jamb = 0.14F;

        // Jambs and lintel: stone-dark, with the faintest cold sheen.
        quad(buffer, matrix, -halfWidth, 0.0F, 0.0F, jamb, height, frameAlpha, 0.05F, 0.055F, 0.07F);
        quad(buffer, matrix, halfWidth - jamb, 0.0F, 0.0F, jamb, height, frameAlpha, 0.05F, 0.055F, 0.07F);
        quad(buffer, matrix, -halfWidth, height - jamb, 0.0F, halfWidth * 2.0F, jamb, frameAlpha, 0.05F, 0.055F, 0.07F);

        // The interior: a recessed plane of near-black, then a second,
        // smaller, darker plane further back — the suggestion of a corridor
        // that does not exist.
        float innerW = (halfWidth - jamb) * 2.0F;
        float innerH = height - jamb;
        quad(buffer, matrix, -innerW * 0.5F, 0.0F, -0.30F, innerW, innerH,
                0.92F * strength * draught, 0.008F, 0.010F, 0.014F);
        float deepW = innerW * 0.55F;
        float deepH = innerH * 0.72F;
        quad(buffer, matrix, -deepW * 0.5F, 0.0F, -0.85F, deepW, deepH,
                0.95F * strength * draught, 0.004F, 0.005F, 0.008F);
        // A cold seam of "light from nowhere" at the corridor's far edge.
        quad(buffer, matrix, -deepW * 0.5F, deepH - 0.03F, -0.85F, deepW, 0.03F,
                0.35F * strength * draught, 0.10F, 0.30F, 0.33F);

        // The reveal: only once the target has committed and the passage
        // starts to fold, two ember eyes are suddenly just... inside, a
        // little too high and too far apart for the doorway. They fold with
        // it. Steady glow, never a flash.
        float watch = (float) (SceneMath.smoothstep(0.04D, 0.22D, collapse)
                * (1.0D - SceneMath.smoothstep(0.50D, 0.80D, collapse)));
        if (watch > 0.001F) {
            float eyeAlpha = 0.60F * strength * watch;
            quad(buffer, matrix, -0.24F, 1.42F, -0.84F, 0.10F, 0.05F, eyeAlpha,
                    ScenePalette.EYE_RED, ScenePalette.EYE_GREEN, ScenePalette.EYE_BLUE);
            quad(buffer, matrix, 0.14F, 1.42F, -0.84F, 0.10F, 0.05F, eyeAlpha,
                    ScenePalette.EYE_RED, ScenePalette.EYE_GREEN, ScenePalette.EYE_BLUE);
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** A vertical quad centred in X at {@code x}, standing on y0, facing ±Z. */
    private static void quad(
            BufferBuilder buffer,
            Matrix4f matrix,
            float x,
            float y,
            float z,
            float width,
            float height,
            float alpha,
            float red,
            float green,
            float blue) {
        buffer.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x, y + height, z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y + height, z).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, x + width, y, z).color(red, green, blue, alpha).endVertex();
    }
}
