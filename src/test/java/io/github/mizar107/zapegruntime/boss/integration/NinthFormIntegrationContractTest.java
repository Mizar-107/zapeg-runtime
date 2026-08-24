package io.github.mizar107.zapegruntime.boss.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.network.SceneNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormIntegrationContractTest {

    private static final Path ROOT = Path.of("src", "main");
    private static final Path JAVA = ROOT.resolve(Path.of(
            "java", "io", "github", "mizar107", "zapegruntime"));

    @Test
    void entrypointAndCommandTreeInstallEveryBossSeamExactlyOnce() throws Exception {
        String entrypoint = Files.readString(JAVA.resolve("ZapeGRuntime.java"));
        String commands = Files.readString(JAVA.resolve(Path.of(
                "server", "SceneServerEvents.java")));

        assertEquals(1, occurrences(entrypoint, "NinthFormEntities.register(modBus)"));
        assertEquals(1, occurrences(entrypoint, "NinthFormSounds.register(modBus)"));
        assertEquals(1, occurrences(commands, "NinthFormCommands.attach(root)"));
        assertFalse(entrypoint.toLowerCase().contains("citizen"));
    }

    @Test
    void gatewayLifecycleAndEntityJoinAreExactAndSignalsAreDeferred() throws Exception {
        String events = Files.readString(JAVA.resolve(Path.of(
                "boss", "encounter", "NinthFormServerEvents.java")));
        String gateway = Files.readString(JAVA.resolve(Path.of(
                "boss", "combat", "ForgeNinthFormEntityGateway.java")));

        assertTrue(events.contains("new ForgeNinthFormEntityGateway("));
        assertTrue(events.contains("NinthFormGatewayRegistry.install(server, gateway)"));
        assertTrue(events.contains("NinthFormGatewayRegistry.uninstall(server, gateway)"));
        assertTrue(events.contains("EntityJoinLevelEvent"));
        assertTrue(events.contains("NinthFormEncounterManager.acceptsEntity("));
        assertTrue(events.contains("gateway.attachJoined("));
        assertTrue(events.contains("drainSignals(event.getServer())"));
        assertTrue(events.indexOf("drainSignals(event.getServer())")
                < events.indexOf("NinthFormEncounterManager.tick(event.getServer())"));
        assertFalse(events.contains("signal -> NinthFormEncounterManager.onCombatSignal"));
        assertTrue(gateway.contains("public boolean attachJoined("));
    }

    @Test
    void originalSoundCuesAndLocalizedPresentationReachCombat() throws Exception {
        String gateway = Files.readString(JAVA.resolve(Path.of(
                "boss", "combat", "ForgeNinthFormEntityGateway.java")));
        String boss = Files.readString(JAVA.resolve(Path.of(
                "boss", "combat", "NinthFormBoss.java")));
        String engine = Files.readString(JAVA.resolve(Path.of(
                "boss", "combat", "NinthFormCombatEngine.java")));

        assertTrue(gateway.contains("NinthFormSounds.AWAKENING.get()"));
        assertTrue(engine.contains("NinthFormSounds.TELEGRAPH.get()"));
        assertTrue(engine.contains("NinthFormSounds.WEAKPOINT_BREAK.get()"));
        assertTrue(boss.contains("NinthFormSounds.BANISH.get()"));
        assertTrue(boss.contains("Component.translatable(\"entity.zapeg_runtime.ninth_form\")"));
    }

    @Test
    void protocolVersionAndToastResourceMatchTheFinalReleaseArtifact() throws Exception {
        assertEquals("11", SceneNetwork.PROTOCOL);
        String properties = Files.readString(Path.of("gradle.properties"));
        assertTrue(properties.contains("mod_version=1.1.0"));

        JsonObject advancement = JsonParser.parseString(Files.readString(ROOT.resolve(Path.of(
                        "resources", "data", "zapeg_runtime", "advancements", "heraldor",
                        "banish_ninth_form.json"))))
                .getAsJsonObject();
        JsonObject display = advancement.getAsJsonObject("display");
        assertEquals(
                "advancements.zapeg_runtime.heraldor.banish_ninth_form.title",
                display.getAsJsonObject("title").get("translate").getAsString());
        assertEquals(
                "advancements.zapeg_runtime.heraldor.banish_ninth_form.description",
                display.getAsJsonObject("description").get("translate").getAsString());
        assertTrue(display.get("show_toast").getAsBoolean());
        assertTrue(display.get("hidden").getAsBoolean());
        assertFalse(advancement.has("rewards"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
