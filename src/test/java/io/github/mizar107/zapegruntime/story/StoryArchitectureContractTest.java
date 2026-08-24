package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.network.SceneNetwork;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StoryArchitectureContractTest {

    private static final Path SOURCE_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime", "story");

    @Test
    void storyFoundationDoesNotLoadOrQueryChunks() throws IOException {
        String allSources = allStorySources();
        assertFalse(allSources.contains("getChunk("));
        assertFalse(allSources.contains("hasChunk("));
        assertFalse(allSources.contains("hasChunkAt("));
        assertFalse(allSources.contains("forceChunk"));
        assertFalse(allSources.contains("ServerLevel"));
        assertFalse(allSources.contains("BlockPos"));
    }

    @Test
    void targetIdentityAndOperatorRecoveryAreUuidOnly() throws IOException {
        String commands = Files.readString(SOURCE_ROOT.resolve("StoryCommands.java"));
        String data = Files.readString(SOURCE_ROOT.resolve("StoryWorldData.java"));
        assertTrue(commands.contains("UuidArgument.uuid()"));
        assertTrue(commands.contains("target_uuid"));
        assertFalse(commands.contains("EntityArgument"));
        assertFalse(commands.contains("GameProfile"));
        assertFalse(commands.contains("getName()"));
        assertFalse(data.contains("PlayerName"));
        assertFalse(data.contains("Username"));
    }

    @Test
    void typedFactApiCannotDispatchCommandsOrFreeFormEventNames() throws IOException {
        String allSources = allStorySources();
        String fact = Files.readString(SOURCE_ROOT.resolve("StoryFact.java"));
        String service = Files.readString(SOURCE_ROOT.resolve("StoryService.java"));
        assertTrue(fact.contains("StoryFactType type"));
        assertTrue(fact.contains("ResourceLocation subject"));
        assertTrue(fact.contains("UUID playerId"));
        assertTrue(fact.contains("long progressEpoch"));
        assertTrue(fact.contains("String expectedNodeId"));
        assertTrue(service.contains("submitIfExpected("));
        assertTrue(fact.contains("replayIdentityFingerprint("));
        assertTrue(service.contains("preflightReceipt("));
        assertFalse(service.contains("hasProcessedFact(playerId, factId)"));
        assertTrue(service.contains("StoryFactGate.prepare("));
        assertFalse(allSources.contains("performCommand"));
        assertFalse(allSources.contains("Commands.perform"));
        assertFalse(allSources.contains("/execute"));
    }

    @Test
    void persistenceAndDatapackCountsAreExplicitlyBounded() throws IOException {
        String data = Files.readString(SOURCE_ROOT.resolve("StoryWorldData.java"));
        String reload = Files.readString(SOURCE_ROOT.resolve("StoryReloadListener.java"));
        assertTrue(data.contains("MAX_PLAYERS = 2_048"));
        assertTrue(data.contains("MAX_PROCESSED_FACTS_PER_PLAYER = 256"));
        assertTrue(data.contains("MAX_RECOVERY_OPERATIONS_PER_PLAYER = 64"));
        assertTrue(reload.contains("MAX_CAMPAIGNS = 8"));
        assertEquals(30, StoryCampaignTestFixtures.campaign().nodes().size());
    }

    @Test
    void journalExtensionOwnsTheSingleBatchThreeProtocolBump() {
        assertEquals("10", SceneNetwork.PROTOCOL);
    }

    @Test
    void storyCommandsAttachToTheExistingTrustedHeraldorRoot() throws IOException {
        Path runtimeRoot = SOURCE_ROOT.getParent();
        String commands = Files.readString(
                runtimeRoot.resolve("server").resolve("HeraldorCommands.java"));
        String events = Files.readString(SOURCE_ROOT.resolve("StoryServerEvents.java"));
        assertTrue(commands.contains("StoryCommands.attach(root)"));
        assertFalse(events.contains("RegisterCommandsEvent"));
        assertFalse(events.contains("Commands.literal(\"heraldor\")"));
    }

    private static String allStorySources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }
}
