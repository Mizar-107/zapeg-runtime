package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BreachArchitectureContractTest {

    @Test
    void rendererIsScreenSpaceOnlyAndTextureFree() throws IOException {
        String renderer = source("client/BreachRenderer.java");
        assertTrue(renderer.contains("GuiGraphics"));
        assertTrue(renderer.contains("graphics.fill"));
        assertFalse(renderer.contains("ClientLevel"));
        assertFalse(renderer.contains("RenderLevelStageEvent"));
        assertFalse(renderer.contains("ResourceLocation"));
        assertFalse(renderer.contains("RenderType"));
        assertFalse(renderer.contains("descriptor.anchor"));
        assertFalse(renderer.contains("getChunk"));
        assertFalse(renderer.contains("playSound"));
    }

    @Test
    void visitationProofComesFromTheGuiHookNotTickOrPacketReceipt() throws IOException {
        String manager = source("client/ClientSceneManager.java");
        String presentation = source("client/BreachPresentation.java");
        assertTrue(presentation.contains("if (presented)"));
        assertTrue(presentation.contains("proof.run()"));
        assertTrue(presentation.contains("markBreachFramePresented("));
        assertTrue(manager.contains("markInGameFallbackRequested()"));
        assertTrue(manager.contains("markInGameFallbackApplied()"));
        int proof = manager.indexOf("static void markBreachFramePresented");
        assertTrue(proof >= 0);
        assertTrue(manager.indexOf("markInGameFallbackApplied()", proof) > proof);
        assertFalse(manager.substring(0, proof).contains("markInGameFallbackApplied()"),
                "ticks and packet acceptance cannot manufacture render proof");
    }

    @Test
    void everyGuiStateHasOneTopmostOrHiddenHudHookWithStateRestoration()
            throws IOException {
        String events = source("client/ClientSceneEvents.java");
        assertTrue(events.contains("Surface.HUD_POST"));
        assertTrue(events.contains("ScreenEvent.Render.Post"));
        assertTrue(events.contains("Surface.SCREEN_POST"));
        assertTrue(events.contains("RenderLevelStageEvent.Stage.AFTER_LEVEL"));
        assertTrue(events.contains("Surface.HIDDEN_HUD_AFTER_LEVEL"));
        assertTrue(events.contains("new Matrix4f(RenderSystem.getProjectionMatrix())"));
        assertTrue(events.contains("RenderSystem.getVertexSorting()"));
        assertTrue(events.contains("modelView.pushPose()"));
        assertTrue(events.contains("modelView.popPose()"));
        assertTrue(events.contains("previousBlendDepth.restore()"));
        assertTrue(events.contains("graphics.flush()"));
        assertFalse(events.contains("options.hideGui ="));
    }

    @Test
    void breachAudioIsTargetLocalBoundedAndOwned() throws IOException {
        String sounds = source("client/SceneSounds.java");
        int start = sounds.indexOf("static void playBreachCue");
        int end = sounds.indexOf("private static Vec3 rotateTargetOffset", start);
        String breach = sounds.substring(start, end);
        assertTrue(breach.contains("playerPosition"));
        assertTrue(breach.contains("HeraldorSounds.KNOCK_01"));
        assertTrue(breach.contains("HeraldorSounds.KNOCK_02"));
        assertTrue(breach.contains("HeraldorSounds.FOOTSTEP_01"));
        assertTrue(breach.contains("HeraldorSounds.FOOTSTEP_02"));
        assertTrue(breach.contains("HeraldorSounds.WHISPER_01"));
        assertTrue(breach.contains("HeraldorSounds.WHISPER_02"));
        assertTrue(breach.contains("HeraldorSounds.MANIFESTATION"));
        assertFalse(breach.contains("descriptor.anchor"));
        assertTrue(sounds.contains("Math.min(0.85F, volume)"));
        assertTrue(sounds.contains("playLocalSound"));
    }

    @Test
    void commonRegistrationIsDedicatedServerSafe() throws IOException {
        String registry = source("sound/HeraldorSounds.java");
        String entrypoint = sourceRoot("ZapeGRuntime.java");
        assertTrue(registry.contains("DeferredRegister.create(ForgeRegistries.SOUND_EVENTS"));
        assertFalse(registry.contains("net.minecraft.client"));
        assertFalse(registry.contains("Dist.CLIENT"));
        assertTrue(entrypoint.contains("HeraldorSounds.register(modBus)"));
    }

    @Test
    void generatorHasNoRecordedOrRemoteInput() throws IOException {
        String generator = Files.readString(Path.of("tools/Generate-HeraldorAudio.ps1"));
        assertTrue(generator.contains("anoisesrc"));
        assertTrue(generator.contains("aevalsrc"));
        assertFalse(generator.contains("http://"));
        assertFalse(generator.contains("https://"));
        assertFalse(generator.contains(".wav"));
        assertFalse(generator.contains(".mp3"));
    }

    private static String source(String relative) throws IOException {
        return sourceRoot("client/../" + relative);
    }

    private static String sourceRoot(String relative) throws IOException {
        Path root = Path.of("src/main/java/io/github/mizar107/zapegruntime");
        return Files.readString(root.resolve(relative).normalize());
    }
}
