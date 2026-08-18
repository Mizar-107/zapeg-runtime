package io.github.mizar107.zapegruntime.client;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = ZapeGRuntime.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientSceneEvents {

    private ClientSceneEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientSceneManager.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientSceneManager.RenderSnapshot snapshot = ClientSceneManager.observe(event);
        if (snapshot == null) {
            return;
        }
        SceneProfile profile = snapshot.descriptor().profile();
        if (profile == SceneProfile.SKY_MARK_01) {
            SkyMarkRenderer.render(snapshot, event);
        } else if (profile == SceneProfile.FALSE_PASSAGE_01) {
            FalsePassageRenderer.render(snapshot, event);
        } else if (profile == SceneProfile.COLOSSUS_01) {
            ColossusRenderer.render(snapshot, event);
        } else {
            ApparitionRenderer.render(snapshot, event);
        }
    }

    /**
     * Bounded camera unease: positional jitter, a brief reveal jolt and an
     * unnatural roll drift, all hard-capped by CameraUnease so the player
     * never loses control for more than a moment.
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] offset = ClientSceneManager.cameraPerturbation((float) event.getPartialTick());
        if (offset[0] == 0.0F && offset[1] == 0.0F && offset[2] == 0.0F) {
            return;
        }
        event.setYaw(event.getYaw() + offset[0]);
        event.setPitch(event.getPitch() + offset[1]);
        event.setRoll(event.getRoll() + offset[2]);
    }

    /**
     * The ambience dip's fog component: at most a few percent of pull-in on
     * vanilla terrain fog. Shader packs that own their fog simply override
     * this; the magnitude is deliberately too small to fight them.
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // FogType.NONE is clear air in 1.20.1: never touch lava, water or
        // powder-snow fog, only the ordinary terrain atmosphere.
        if (event.getType() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float dip = ClientSceneManager.fogDip((float) event.getPartialTick());
        if (dip <= 0.0F) {
            return;
        }
        event.scaleFarPlaneDistance(1.0F - 0.06F * dip);
        event.scaleNearPlaneDistance(1.0F - 0.04F * dip);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float intensity = ClientSceneManager.guiEffectIntensity(event.getPartialTick());
        if (intensity <= 0.0F) {
            return;
        }
        SceneProfile profile = ClientSceneManager.activeProfile();
        if (profile == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        long seed = ClientSceneManager.visualSeed();
        double age = ClientSceneManager.bodyAgeWithPartial(event.getPartialTick());
        ClientSceneManager.ScenePhase phase = ClientSceneManager.scenePhase();
        if (phase == ClientSceneManager.ScenePhase.PRELUDE) {
            drawPreludeDim(graphics, width, height, intensity);
            return;
        }
        if (phase == ClientSceneManager.ScenePhase.ENCORE) {
            // Sound-only profiles keep the screen clean even for the encore.
            if (profile != SceneProfile.FOOTSTEPS_01) {
                drawEncoreFlash(graphics, width, height, intensity, seed);
            }
            return;
        }
        switch (profile) {
            case ECHO_01 -> drawEdgeFaults(graphics, width, height, intensity, seed, age);
            case THRESHOLD_01 -> drawThresholdSlit(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age,
                    ClientSceneManager.gazeProgress());
            case MOTION_ECHO_01 -> drawMotionEchoFaults(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age);
            case LIGHT_FAULT_01 -> drawLightFault(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed,
                    age);
            case PERIPHERAL_01 -> drawPeripheralEdge(
                    graphics,
                    width,
                    height,
                    intensity,
                    seed);
            case SKY_MARK_01 -> drawSkyMarkWeight(graphics, width, height, intensity);
            case FALSE_PASSAGE_01 -> drawPassageSeams(graphics, width, height, intensity, seed, age);
            case CHROMA_BREAK_01 -> drawChromaBreak(graphics, width, height, intensity, seed, age);
            case NEAR_MISS_01, WHISPER_STEPS_01, FOOTSTEPS_01, COLOSSUS_01 -> {
                // Sound-only / crossing / colossus scenes: the screen must
                // stay clean; the ground shake carries the colossus.
            }
        }
    }

    /**
     * The sky mark's screen component: the top of the screen gains a faint
     * cold weight, as if the sky itself is slightly too heavy. No flashing.
     */
    private static void drawSkyMarkWeight(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity) {
        int strips = 5;
        int band = Math.max(2, height / 10);
        for (int strip = 0; strip < strips; strip++) {
            int alpha = Math.round(26.0F * intensity * (strips - strip) / strips);
            if (alpha <= 0) {
                continue;
            }
            int y0 = strip * band / strips * 2;
            graphics.fill(0, y0, width, y0 + Math.max(1, band / strips), argb(alpha, 2, 6, 10));
        }
    }

    /**
     * Two thin vertical seams standing on the screen while the false passage
     * waits — the faintest suggestion of a frame that should not be there.
     */
    private static void drawPassageSeams(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        float pulse = 0.75F
                + 0.25F * (float) Math.sin(age * 0.13D + Math.floorMod(seed, 61L));
        int alpha = Math.max(2, Math.min(20, Math.round(20.0F * intensity * pulse)));
        int left = width / 4 + Math.floorMod(seed, Math.max(1, width / 12));
        int right = width * 3 / 4 - Math.floorMod(seed >>> 9, Math.max(1, width / 12));
        graphics.fill(left, height / 5, left + 1, height * 4 / 5, argb(alpha, 3, 10, 12));
        graphics.fill(right, height / 5, right + 1, height * 4 / 5, argb(alpha, 3, 10, 12));
    }

    /**
     * The corrupted-recording tear: a handful of horizontal bands whose top
     * and bottom edges split into offset red/cyan fringe pairs, over a faint
     * grey tracking wash. Strictly bounded: the pulse is a ~0.44 Hz sine,
     * band alphas stay in the teens, and no band ever covers the whole
     * screen at once — unease, never a strobe.
     */
    private static void drawChromaBreak(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int wash = Math.max(2, Math.min(10, Math.round(12.0F * intensity)));
        graphics.fill(0, 0, width, height, argb(wash, 8, 9, 11));

        int bands = 5;
        int scroll = (int) Math.floor(age * 1.7D);
        for (int band = 0; band < bands; band++) {
            int bandSeed = Math.floorMod((int) (seed >>> (band * 9)), 997);
            int y = Math.floorMod(
                    bandSeed + scroll * (1 + band % 2) + band * 137,
                    Math.max(1, height));
            int bandHeight = 3 + Math.floorMod(bandSeed >>> 3, 8);
            int bandAlpha = Math.max(3, Math.min(16, Math.round(16.0F * intensity)));
            // The band itself darkens slightly, as if the signal dropped.
            graphics.fill(
                    0,
                    y,
                    width,
                    Math.min(height, y + bandHeight),
                    argb(bandAlpha, 4, 5, 7));
            // The split: a red fringe slipped a few pixels left of a cyan
            // fringe at the band's torn edges.
            int shift = 1 + Math.floorMod(bandSeed >>> 6, 3);
            int fringeAlpha = Math.max(2, bandAlpha - 2);
            graphics.fill(
                    0,
                    Math.max(0, y - 1),
                    Math.max(0, width - shift),
                    y,
                    argb(fringeAlpha, 110, 0, 10));
            graphics.fill(
                    Math.min(width, shift),
                    Math.min(height, y + bandHeight),
                    width,
                    Math.min(height, y + bandHeight + 1),
                    argb(fringeAlpha, 0, 80, 92));
        }
    }

    /** A faint dark wedge hugging one screen edge, seeded per scene. */
    private static void drawPeripheralEdge(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed) {
        int alpha = Math.round(46.0F * intensity);
        if (alpha <= 0) {
            return;
        }
        boolean left = (seed & 1L) == 0L;
        int wedge = Math.max(10, width / 14);
        int strips = 6;
        int stripWidth = Math.max(1, wedge / strips);
        for (int strip = 0; strip < strips; strip++) {
            int stripAlpha = alpha * (strips - strip) / strips;
            int x0 = left
                    ? strip * stripWidth
                    : width - (strip + 1) * stripWidth;
            graphics.fill(x0, 0, x0 + stripWidth, height, argb(stripAlpha, 1, 1, 3));
        }
        int foot = Math.round(18.0F * intensity);
        if (foot > 0) {
            graphics.fill(0, height - height / 8, width, height, argb(foot, 1, 1, 3));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSceneManager.clearWithoutAcknowledgement();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ClientSceneManager.clearWithoutAcknowledgement();
        }
    }

    private static void drawEdgeFaults(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int baseAlpha = Math.max(5, Math.min(42, Math.round(42.0F * intensity)));
        int phase = (int) Math.floor(age * 0.65D + Math.floorMod(seed, 97L));
        for (int index = 0; index < 4; index++) {
            int y = Math.floorMod(phase * 17 + index * 53, Math.max(1, height));
            int length = 10 + Math.floorMod((int) (seed >>> (index * 7)), Math.max(11, width / 5));
            int thickness = index == 0 ? 2 : 1;
            int alpha = Math.max(3, baseAlpha - index * 5);
            int dark = argb(alpha, 2, 2, 5);
            int cyan = argb(Math.max(2, alpha / 2), 0, 90, 105);
            int red = argb(Math.max(2, alpha / 2), 120, 0, 8);
            graphics.fill(0, y, Math.min(width, length), Math.min(height, y + thickness), dark);
            graphics.fill(
                    Math.max(0, width - length),
                    Math.max(0, height - y - thickness),
                    width,
                    Math.max(0, height - y),
                    dark);
            graphics.fill(0, Math.min(height, y + 2), Math.min(width, length / 2), Math.min(height, y + 3), cyan);
            graphics.fill(
                    Math.max(0, width - length / 2),
                    Math.max(0, height - y - 3),
                    width,
                    Math.max(0, height - y - 2),
                    red);
        }
    }

    private static void drawThresholdSlit(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age,
            float gazeProgress) {
        boolean left = (seed & 1L) == 0L;
        int maxDepth = Math.max(3, Math.min(18, width / 18));
        int depth = Math.max(
                2,
                Math.round(maxDepth * intensity * (1.0F - gazeProgress * 0.55F)));
        int alpha = Math.max(8, Math.min(58, Math.round(58.0F * intensity)));
        int edgeColor = argb(alpha, 1, 2, 4);
        int seamColor = argb(Math.max(3, alpha / 3), 4, 66, 70);
        if (left) {
            graphics.fill(0, 0, depth, height, edgeColor);
            graphics.fill(depth, 0, Math.min(width, depth + 1), height, seamColor);
        } else {
            graphics.fill(Math.max(0, width - depth), 0, width, height, edgeColor);
            graphics.fill(
                    Math.max(0, width - depth - 1),
                    0,
                    Math.max(0, width - depth),
                    height,
                    seamColor);
        }

        int slitY = Math.floorMod(
                (int) Math.floor(age * 0.33D + Math.floorMod(seed, 113L)),
                Math.max(1, height));
        int slitLength = Math.max(8, width / 9);
        int slitStart = left ? 0 : Math.max(0, width - slitLength);
        graphics.fill(
                slitStart,
                slitY,
                left ? Math.min(width, slitLength) : width,
                Math.min(height, slitY + 1),
                argb(Math.max(2, alpha / 4), 55, 1, 7));
    }

    private static void drawMotionEchoFaults(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        int alpha = Math.max(3, Math.min(24, Math.round(24.0F * intensity)));
        int drift = Math.floorMod((int) Math.floor(age * 0.9D), 11) - 5;
        for (int copy = 0; copy < 3; copy++) {
            int y = Math.floorMod(
                    (int) (seed >>> (copy * 11)) + copy * 71 + (int) age,
                    Math.max(1, height));
            int inset = 8 + copy * 6;
            int color = argb(Math.max(2, alpha - copy * 5), 8, 45, 52);
            graphics.fill(
                    Math.max(0, inset + drift * copy),
                    y,
                    Math.max(0, width - inset + drift * copy),
                    Math.min(height, y + 1),
                    color);
        }
    }

    private static void drawLightFault(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed,
            double age) {
        float flicker = 0.85F
                + 0.15F * (float) Math.sin(age * 3.1D + Math.floorMod(seed, 59L));
        int washAlpha = Math.max(4, Math.min(30, Math.round(30.0F * intensity * flicker)));
        graphics.fill(0, 0, width, height, argb(washAlpha, 1, 5, 8));

        int step = Math.max(1, Math.min(width, height) / 90);
        int edgeAlpha = Math.max(8, Math.min(64, Math.round(64.0F * intensity)));
        for (int layer = 0; layer < 6; layer++) {
            int inset = layer * step;
            int next = inset + step;
            int alpha = Math.max(2, edgeAlpha - layer * Math.max(1, edgeAlpha / 7));
            int color = argb(alpha, 0, 1, 3);
            graphics.fill(inset, inset, Math.max(inset, width - inset), next, color);
            graphics.fill(
                    inset,
                    Math.max(next, height - next),
                    Math.max(inset, width - inset),
                    Math.max(next, height - inset),
                    color);
            graphics.fill(inset, next, next, Math.max(next, height - next), color);
            graphics.fill(
                    Math.max(next, width - next),
                    next,
                    Math.max(next, width - inset),
                    Math.max(next, height - next),
                    color);
        }

        int bandX = Math.floorMod(
                (int) Math.floor(age * 0.21D + Math.floorMod(seed, 131L)),
                Math.max(1, width));
        graphics.fill(
                bandX,
                0,
                Math.min(width, bandX + Math.max(1, width / 180)),
                height,
                argb(Math.max(2, washAlpha / 2), 2, 38, 43));

        int counterBandX = Math.floorMod(
                (int) Math.floor(-age * 0.13D + Math.floorMod(seed, 67L)),
                Math.max(1, width));
        graphics.fill(
                counterBandX,
                0,
                Math.min(width, counterBandX + Math.max(1, width / 240)),
                height,
                argb(Math.max(2, washAlpha / 3), 3, 30, 36));
    }

    /**
     * The ambience dip: the whole screen cools and darkens a few percent while
     * the prelude swell builds. Deliberately far below any flash threshold.
     */
    private static void drawPreludeDim(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity) {
        int wash = Math.max(2, Math.min(12, Math.round(86.0F * intensity)));
        graphics.fill(0, 0, width, height, argb(wash, 1, 3, 6));
        int edge = Math.max(2, Math.min(20, Math.round(140.0F * intensity)));
        int band = Math.max(2, height / 12);
        graphics.fill(0, 0, width, band, argb(edge, 0, 1, 3));
        graphics.fill(0, height - band, width, height, argb(edge, 0, 1, 3));
    }

    /**
     * The encore's single final beat on screen: one slow cold vignette pulse
     * and one thin fault line. Bounded to a 30-tick sine envelope, so there
     * is no strobing and no full-screen luminance change.
     */
    private static void drawEncoreFlash(
            GuiGraphics graphics,
            int width,
            int height,
            float intensity,
            long seed) {
        int edgeAlpha = Math.max(3, Math.min(26, Math.round(52.0F * intensity)));
        int step = Math.max(2, Math.min(width, height) / 60);
        for (int layer = 0; layer < 4; layer++) {
            int inset = layer * step;
            int alpha = Math.max(2, edgeAlpha - layer * Math.max(1, edgeAlpha / 5));
            int color = argb(alpha, 0, 2, 4);
            graphics.fill(inset, inset, Math.max(inset, width - inset), inset + step, color);
            graphics.fill(
                    inset,
                    Math.max(inset, height - inset - step),
                    Math.max(inset, width - inset),
                    Math.max(inset + step, height - inset),
                    color);
        }
        int lineY = Math.floorMod((int) (seed >>> 5), Math.max(1, height));
        int lineAlpha = Math.max(2, Math.min(18, Math.round(36.0F * intensity)));
        graphics.fill(0, lineY, width, Math.min(height, lineY + 1), argb(lineAlpha, 3, 40, 44));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24
                | (red & 0xFF) << 16
                | (green & 0xFF) << 8
                | (blue & 0xFF);
    }
}
