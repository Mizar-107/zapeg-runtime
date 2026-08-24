package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TimelineArchitectureContractTest {

    private static final Path RUNTIME_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");
    private static final Path TIMELINE_ROOT = RUNTIME_ROOT.resolve("timeline");

    @Test
    void sessionsUseBoundedUuidLookupsWithoutEntityScansOrChunkForcing()
            throws IOException {
        String manager = Files.readString(TIMELINE_ROOT.resolve("TimelineServerManager.java"));
        String data = Files.readString(TIMELINE_ROOT.resolve("TimelineSessionData.java"));
        String scene = Files.readString(
                RUNTIME_ROOT.resolve("server").resolve("SceneServerManager.java"));
        String loadedQueries = Files.readString(
                RUNTIME_ROOT.resolve("server").resolve("LoadedSceneQueries.java"));

        assertTrue(manager.contains("getPlayer(session.targetId())"));
        assertTrue(data.contains("MAX_ACTIVE_SESSIONS = 256"));
        assertTrue(data.contains("MAX_TERMINAL_RESULTS = 4_096"));
        assertTrue(scene.contains("activeByTarget"));
        for (String source : java.util.List.of(manager, data, scene, loadedQueries)) {
            assertFalse(source.contains("getAllEntities("));
            assertFalse(source.contains("getAllLevels("));
            assertFalse(source.contains(".getChunk("));
            assertFalse(source.contains("forceChunk"));
        }
        assertTrue(loadedQueries.contains("getChunkSource().hasChunk("));
    }

    @Test
    void datapackReloadIsAtomicStrictAndActuallyRegistered() throws IOException {
        String listener = Files.readString(
                TIMELINE_ROOT.resolve("TimelineReloadListener.java"));
        String parser = Files.readString(TIMELINE_ROOT.resolve("TimelineJsonParser.java"));
        String events = Files.readString(
                RUNTIME_ROOT.resolve("server").resolve("SceneServerEvents.java"));

        assertTrue(listener.contains("SimpleJsonResourceReloadListener"));
        assertTrue(listener.contains("protected Map<ResourceLocation, JsonElement> prepare("));
        assertTrue(listener.contains("resourceManager.listResources("));
        assertTrue(listener.contains("JsonParser.parseReader(reader)"));
        assertTrue(listener.contains("TimelineRegistry.publish(parsed)"));
        assertTrue(listener.indexOf("parseResources(resources)")
                < listener.indexOf("TimelineRegistry.publish(parsed)"));
        assertTrue(parser.contains("rejectUnknown"));
        assertTrue(events.contains("AddReloadListenerEvent"));
        assertTrue(events.contains("event.addListener(new TimelineReloadListener())"));
        assertTrue(Files.exists(Path.of(
                "src", "main", "resources", "data", "zapeg_runtime",
                "heraldor_timelines", "dread_approach_01.json")));
        assertTrue(Files.exists(Path.of(
                "src", "main", "resources", "data", "zapeg_runtime",
                "heraldor_timelines", "breach_01.json")));
    }

    @Test
    void typedCommandsAndCurrentProtocolAreBothWiredExactlyOnce() throws IOException {
        String commands = Files.readString(TIMELINE_ROOT.resolve("TimelineCommands.java"));
        String events = Files.readString(
                RUNTIME_ROOT.resolve("server").resolve("SceneServerEvents.java"));
        String network = Files.readString(
                RUNTIME_ROOT.resolve("network").resolve("SceneNetwork.java"));

        assertTrue(commands.contains("EntityArgument.player()"));
        assertTrue(commands.contains("UuidArgument.uuid()"));
        assertTrue(commands.contains("ResourceLocationArgument.id()"));
        assertTrue(events.contains("SceneCommands.register(event)"));
        assertTrue(events.contains("TimelineCommands.attach(root)"));
        assertEquals(1, occurrences(events, "HeraldorCommands.register("));
        assertTrue(network.contains("public static final String PROTOCOL = \"10\""));
    }

    @Test
    void seededDispatchStaysTargetPrivateAndRetainsDuplicateBarrier() throws IOException {
        String sceneManager = Files.readString(
                RUNTIME_ROOT.resolve("server").resolve("SceneServerManager.java"));
        String network = Files.readString(
                RUNTIME_ROOT.resolve("network").resolve("SceneNetwork.java"));

        assertTrue(sceneManager.contains("dispatchTimeline("));
        assertTrue(sceneManager.contains("replayData.claimForDispatch("));
        assertFalse(sceneManager.contains("replayData.reserve(replayIdentity)"));
        assertTrue(sceneManager.contains("DispatchClaim.ALREADY_APPLIED"));
        assertTrue(sceneManager.contains("replayData.rollbackReservation(replayIdentity)"));
        assertTrue(sceneManager.contains("replayData.claimExternalForDispatch("));
        assertTrue(sceneManager.contains("replayData.markExternalApplied("));
        assertTrue(sceneManager.contains("replayData.isReserved(replayIdentity)"));
        assertTrue(sceneManager.contains("SceneLedgerData.get(server).contains(eventId)"));
        assertTrue(sceneManager.contains("visualSeed == null"));
        assertTrue(sceneManager.contains("ScenePlacement.findSeeded("));
        assertTrue(network.contains("PacketDistributor.PLAYER"));
        assertFalse(network.contains("PacketDistributor.ALL"));
    }

    @Test
    void definitionValidationPrecedesLifecycleAndDispatch() throws IOException {
        String engine = Files.readString(TIMELINE_ROOT.resolve("TimelineEngine.java"));
        String session = Files.readString(TIMELINE_ROOT.resolve("TimelineSession.java"));
        String determinism = Files.readString(
                TIMELINE_ROOT.resolve("TimelineDeterminism.java"));
        int validation = engine.indexOf("input.validationFailure(definition)");
        int lifecycle = engine.indexOf("policies.disconnect()");
        int execute = engine.indexOf("executor.execute(");

        assertTrue(validation >= 0 && validation < lifecycle && validation < execute);
        assertTrue(session.contains("TimelineDeterminism.sessionSeed("));
        assertTrue(session.contains("CURSOR_OUTSIDE_DEFINITION"));
        assertTrue(determinism.contains("requireUuid(targetId)"));
        assertTrue(determinism.contains("\":placement:\""));
    }

    @Test
    void schemaOnePersistenceQuarantinesInsteadOfSkippingRecords() throws IOException {
        String data = Files.readString(TIMELINE_ROOT.resolve("TimelineSessionData.java"));

        assertTrue(data.contains("requireCompoundList(root, TERMINAL)"));
        assertTrue(data.contains("requireExactFields(encoded, TERMINAL_FIELDS"));
        assertTrue(data.contains("DataHealth.CORRUPT"));
        assertFalse(data.contains("Corrupt siblings do not erase"));
        assertFalse(data.contains("Math.min(encodedTerminal.size()"));
    }

    @Test
    void nativeTimelineAuthorityHasNoSidecarDependency() throws IOException {
        StringBuilder combined = new StringBuilder();
        try (Stream<Path> sources = Files.list(TIMELINE_ROOT)) {
            for (Path source : sources.filter(
                    path -> path.toString().endsWith(".java")).toList()) {
                combined.append(Files.readString(source));
            }
        }
        for (String forbidden : java.util.List.of(
                "KubeJS", "RCON", "SQLite", "Discord", "ProcessBuilder")) {
            assertFalse(combined.toString().contains(forbidden));
        }
    }

    private static int occurrences(String input, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = input.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }
}
