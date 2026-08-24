package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VoiceCompatibilityArchitectureTest {

    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");

    @Test
    void exactVoiceDispatchIsRehearsalOnlyAndHasNoProofOrReplayIdentity()
            throws IOException {
        String manager = Files.readString(
                JAVA_ROOT.resolve("server").resolve("SceneServerManager.java"));
        String method = slice(
                manager,
                "public static DispatchResult dispatchVoiceRehearsal(",
                "/**\n     * Director-only provenance path");

        assertTrue(method.contains("plan.visualSeed(eventId)"));
        assertTrue(method.contains("\n                true,"),
                "Voice descriptor must be rehearsal-marked");
        assertTrue(method.endsWith("null,\n                null);\n    }\n\n    "),
                "timeline replay and Director proof identities must both be null");
        assertFalse(method.contains("dispatchDirector"));
        assertFalse(method.contains("SceneLedgerData"));
        assertFalse(method.contains("TimelineReplayData"));
        assertFalse(method.contains("StoryService"));
    }

    @Test
    void managerOnlyCoordinatesTheExistingScenePathAndExactCallbacks()
            throws IOException {
        String voice = Files.readString(
                JAVA_ROOT.resolve("director").resolve("VoiceRehearsalManager.java"));
        String scenes = Files.readString(
                JAVA_ROOT.resolve("server").resolve("SceneServerManager.java"));

        assertTrue(voice.contains("SceneServerManager.dispatchVoiceRehearsal("));
        assertTrue(voice.contains("previous.isPresent() && previous.get().active()"),
                "repeat commands must preserve the exact active rehearsal");
        assertFalse(voice.contains("dispatchDirector("));
        assertFalse(voice.contains("StoryService"));
        assertFalse(voice.contains("StoryFact"));
        assertFalse(voice.contains("TimelineReplay"));
        assertFalse(voice.contains("io.github.mizar107.zapegruntime.client"));

        assertTrue(scenes.contains("VoiceRehearsalManager.onAcknowledgement("));
        assertTrue(scenes.contains("VoiceRehearsalManager.onCancelled("));
    }

    @Test
    void commandAndAllLifecycleClearsAreWiredOnTheCommonServerBus()
            throws IOException {
        String events = Files.readString(
                JAVA_ROOT.resolve("server").resolve("SceneServerEvents.java"));
        String commands = Files.readString(
                JAVA_ROOT.resolve("director").resolve("VoiceCommands.java"));

        assertTrue(events.contains("VoiceCommands.attach(root)"));
        assertTrue(events.contains("onLogout(PlayerEvent.PlayerLoggedOutEvent event)"));
        assertTrue(events.contains("onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event)"));
        assertTrue(events.contains("onDeath(LivingDeathEvent event)"));
        assertTrue(events.contains("VoiceRehearsalManager.clearTarget("));
        assertTrue(events.contains("VoiceRehearsalManager.shutdown()"));
        assertFalse(commands.contains(".requires("),
                "Voice must inherit the root CommandSourcePolicy without a bypass");
        assertFalse(commands.contains("io.github.mizar107.zapegruntime.client"));
    }

    @Test
    void migrationAndTwoClientPrivacySmokeStayDocumented() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        String director = Files.readString(Path.of("docs", "HERALDOR-DIRECTOR.md"));

        assertTrue(readme.contains("/heraldor voice rehearse Mizar__107"));
        assertTrue(readme.contains("/heraldor voice status Mizar__107"));
        assertTrue(readme.contains("old `/zapeg-lore voice rehearse` command"));
        assertTrue(readme.contains("keep a second client beside the target"));
        assertTrue(director.contains("external Discord test-channel clip"));
        assertTrue(director.contains("target-private BREACH binding"));
        assertTrue(director.contains("/heraldor voice rehearse <online_player> voice_02"));
        assertTrue(director.contains("The observer must receive no Voice scene"));
        assertTrue(director.contains("leave the target's Director/story status unchanged"));
    }

    private static String slice(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        if (start < 0 || end < 0) {
            throw new AssertionError("expected source tokens were not found");
        }
        return source.substring(start, end);
    }
}
