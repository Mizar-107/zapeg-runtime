package io.github.mizar107.zapegruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PresenceIdentityContractTest {

    private static final Path MAIN = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");

    @Test
    void apparitionsDoNotUseVanillaZombieIdentity() throws IOException {
        String renderer = Files.readString(MAIN.resolve("client").resolve("ApparitionRenderer.java"));
        String model = Files.readString(MAIN.resolve("client").resolve("ApparitionModel.java"));
        String events = Files.readString(MAIN.resolve("client").resolve("ClientModEvents.java"));
        assertFalse(renderer.contains("zombie.png"));
        assertFalse(renderer.contains("ModelLayers.ZOMBIE"));
        assertTrue(renderer.contains("ApparitionModel.LAYER_LOCATION"));
        assertTrue(renderer.contains("ApparitionModel.TEXTURE"));
        assertTrue(model.contains("createBodyLayer"));
        assertTrue(events.contains("ApparitionModel.LAYER_LOCATION"));
        assertTrue(Files.isRegularFile(Path.of(
                "src", "main", "resources", "assets", "zapeg_runtime",
                "textures", "entity", "apparition.png")));
    }

    @Test
    void servantsUseACustomRendererAndHideNametags() throws IOException {
        String events = Files.readString(
                MAIN.resolve("servant").resolve("client").resolve("ServantClientEvents.java"));
        String renderer = Files.readString(
                MAIN.resolve("servant").resolve("client").resolve("ServantRenderer.java"));
        String entity = Files.readString(MAIN.resolve("servant").resolve("HeraldorServant.java"));
        assertFalse(events.contains("WitherSkeletonRenderer"));
        assertTrue(events.contains("ServantRenderer::new"));
        assertTrue(renderer.contains("shouldShowName"));
        assertTrue(renderer.contains("return false"));
        assertTrue(renderer.contains("servant_stalker.png"));
        assertTrue(renderer.contains("servant_herald.png"));
        assertTrue(renderer.contains("servant_binder.png"));
        assertTrue(entity.contains("setCustomNameVisible(false)"));
        assertFalse(entity.contains("SoundEvents.WITHER_SKELETON"));
        assertFalse(entity.contains("WITHER_SKELETON_AMBIENT"));
        assertTrue(entity.contains("HeraldorSounds.SERVANT_AMBIENT"));
    }

    @Test
    void ninthFormCombatDropsVanillaIdentitySounds() throws IOException {
        String engine = Files.readString(
                MAIN.resolve("boss").resolve("combat").resolve("NinthFormCombatEngine.java"));
        String boss = Files.readString(
                MAIN.resolve("boss").resolve("combat").resolve("NinthFormBoss.java"));
        assertFalse(engine.contains("GENERIC_EXPLODE"));
        assertFalse(engine.contains("SoundEvents."));
        assertTrue(engine.contains("NinthFormSounds.IMPACT.get()"));
        assertFalse(boss.contains("ELDER_GUARDIAN"));
        assertTrue(boss.contains("NinthFormSounds.HURT.get()"));
        assertTrue(boss.contains("NinthFormSounds.DEATH.get()"));
        assertTrue(boss.contains("ParticleTypes.SOUL"));
        assertTrue(boss.contains("ParticleTypes.REVERSE_PORTAL"));
        assertTrue(boss.contains("ParticleTypes.END_ROD"));
    }

    @Test
    void protocolStaysEleven() throws IOException {
        String network = Files.readString(MAIN.resolve("network").resolve("SceneNetwork.java"));
        assertTrue(network.contains("public static final String PROTOCOL = \"11\""));
    }
}
