package io.github.mizar107.zapegruntime.boss.presentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormPresentationArchitectureTest {

    private static final Path MAIN = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");
    private static final Path PRESENTATION = MAIN.resolve("boss").resolve("presentation");

    @Test
    void rendererAndLayerRegistrationAreClientOnlyAndLocallyOwned() throws IOException {
        String events = source("NinthFormClientEvents.java");
        String renderer = source("NinthFormRenderer.java");
        String global = Files.readString(MAIN.resolve("ZapeGRuntime.java"));

        assertTrue(events.contains("value = Dist.CLIENT"));
        assertTrue(events.contains("EntityRenderersEvent.RegisterLayerDefinitions"));
        assertTrue(events.contains("NinthFormModel::createBodyLayer"));
        assertTrue(events.contains("NinthFormRenderer::new"));
        assertTrue(renderer.contains("LivingEntityRenderer<NinthFormBoss, NinthFormModel>"));
        assertFalse(global.contains("NinthFormClientEvents"));
        assertFalse(global.contains("NinthFormRenderer"));
        assertTrue(global.contains("NinthFormSounds.register(modBus)"));
    }

    @Test
    void modelNamesAndRendersEveryNativePartWithoutThirdPartyAssets() throws IOException {
        String model = source("NinthFormModel.java");
        String all = allPresentationSource().toLowerCase();
        for (String part : new String[] {
            "PROW_LANTERN", "PORT_MOORING", "STARBOARD_MOORING",
            "KEEL_HEART", "ARMORED_HULL_AFT"
        }) {
            assertTrue(model.contains("NinthFormPartKind." + part), part);
        }
        assertTrue(model.contains("addNativePart("));
        assertTrue(model.contains("kind.lateralOffset()"));
        assertTrue(model.contains("modelForward(kind.forwardOffset())"));
        assertTrue(model.contains("return -pixels(worldForward)"));
        assertTrue(model.contains("LivingEntityRenderer turns the model by 180 - yaw"));
        assertTrue(model.contains("modelPartCenterY(kind)"));
        assertTrue(model.contains("kind.verticalOffset() + kind.height() / 2.0D"));
        assertTrue(model.contains("PartEntity AABBs use [verticalOffset, verticalOffset + height]"));
        assertFalse(all.contains("cataclysm"));
        assertFalse(all.contains("aquamirae"));
        assertFalse(all.contains("geckolib"));
    }

    @Test
    void glowIsSparseBoundedAndDerivedOnlyFromSyncedCombatState() throws IOException {
        String layer = source("NinthFormEmissiveLayer.java");
        String state = source("NinthFormRenderState.java");
        assertTrue(layer.contains("LightTexture.FULL_BRIGHT"));
        assertTrue(layer.contains("configureEmissive(state)"));
        assertTrue(layer.contains("state.emissiveAlpha()"));
        assertTrue(state.contains("MAX_EMISSIVE_ALPHA = 0.68F"));
        assertTrue(state.contains("brokenPointMask & 0b001"));
        assertTrue(state.contains("AttackWindow.WINDUP"));
        assertFalse(allPresentationSource().contains("Minecraft.getInstance"));
    }

    @Test
    void presentationCannotLoadChunksRunCommandsOrDependOnUsernames() throws IOException {
        String all = allPresentationSource();
        for (String forbidden : new String[] {
            "getChunk(", "hasChunk(", "setChunkForced", "teleportToWithTicket",
            "Commands.", "performCommand", "getGameProfile", "getName().getString"
        }) {
            assertFalse(all.contains(forbidden), forbidden);
        }
        assertFalse(all.contains("net.minecraft.server"));
    }

    @Test
    void soundRegistryHasAnExplicitUnwiredIntegrationSeam() throws IOException {
        String sounds = source("NinthFormSounds.java");
        assertTrue(sounds.contains("DeferredRegister<SoundEvent>"));
        assertTrue(sounds.contains("ninth_form_awakening"));
        assertTrue(sounds.contains("ninth_form_telegraph"));
        assertTrue(sounds.contains("ninth_form_weakpoint_break"));
        assertTrue(sounds.contains("ninth_form_banish"));
        assertTrue(sounds.contains("ninth_form_impact"));
        assertTrue(sounds.contains("ninth_form_hurt"));
        assertTrue(sounds.contains("ninth_form_death"));
        assertTrue(sounds.contains("ninth_form_bed"));
        assertTrue(sounds.contains("public static void register(IEventBus modBus)"));
    }

    private static String source(String filename) throws IOException {
        return Files.readString(PRESENTATION.resolve(filename));
    }

    private static String allPresentationSource() throws IOException {
        StringBuilder joined = new StringBuilder();
        try (var files = Files.list(PRESENTATION)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                joined.append(Files.readString(file));
            }
        }
        return joined.toString();
    }
}
