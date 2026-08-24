package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class QuestArchitectureContractTest {

    private static final Path SOURCE_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime", "quest");

    @Test
    void actionsAreUuidTypedAndNeverDispatchCommandsOrMatchNames() throws IOException {
        String sources = allSources();
        String access = Files.readString(SOURCE_ROOT.resolve("QuestStoryAccess.java"));
        String ids = Files.readString(SOURCE_ROOT.resolve("QuestFactIds.java"));

        assertTrue(access.contains("StoryService.submitIfExpected("));
        assertTrue(access.contains("player.getUUID()"));
        assertTrue(ids.contains("playerId.toString()"));
        assertTrue(ids.contains("Long.toString(recoveryEpoch)"));
        assertTrue(ids.contains("action.subject().toString()"));
        assertFalse(sources.contains("performCommand"));
        assertFalse(sources.contains("Commands.perform"));
        assertFalse(sources.contains("getName()"));
        assertFalse(sources.contains("getScoreboardName"));
        assertFalse(sources.contains("GameProfile"));
        assertFalse(sources.contains("Citizen"));
    }

    @Test
    void worldQueriesAreLoadedLocalAndNeverForceOrScanChunks() throws IOException {
        String sources = allSources();
        String manager = Files.readString(SOURCE_ROOT.resolve("QuestActionManager.java"));

        assertTrue(manager.contains("level.hasChunkAt(pos)"));
        assertTrue(manager.contains("getEntitiesOfClass("));
        assertTrue(manager.contains("player.getBoundingBox().inflate(5.0D)"));
        assertFalse(sources.contains(".getChunk("));
        assertFalse(sources.contains("getChunkSource("));
        assertFalse(sources.contains("forceChunk"));
        assertFalse(sources.contains("findNearest"));
        assertFalse(sources.contains("getAllEntities"));
        assertFalse(sources.contains("getAllLevels"));
    }

    @Test
    void exactExpectedGatePrecedesAllTrackingAndBellCountsOnlyAcceptedRings()
            throws IOException {
        String manager = Files.readString(SOURCE_ROOT.resolve("QuestActionManager.java"));
        String rightClick = slice(manager, "static void handleRightClick", "static void reset");
        String tickPlayer = slice(manager, "private static void tickPlayer", "private static QuestProgressPolicy.Progress updateBackward");

        assertTrue(rightClick.indexOf("QuestStoryAccess.expected(player)")
                < rightClick.indexOf("handleBell(player"));
        assertTrue(tickPlayer.indexOf("QuestStoryAccess.expected(player)")
                < tickPlayer.indexOf("matchingSession("));
        assertTrue(manager.contains("instanceof BellBlockEntity"));
        assertTrue(manager.contains("bell.onHit(level, state, event.getHitVec(), player, true)"));
        assertTrue(manager.indexOf("boolean accepted = bell.onHit")
                < manager.indexOf("QuestBellPolicy.recordAcceptedRing("));
        assertTrue(manager.contains("InteractionHand.MAIN_HAND"));
        assertTrue(manager.contains("getCollisionShape(level, pos)"));
        assertTrue(manager.contains("QuestDoorContactPolicy.touchesClosedDoor("));
    }

    @Test
    void ritualCommitPrecedesOneShotOfferingConsumptionAndFeedbackIsLocal()
            throws IOException {
        String manager = Files.readString(SOURCE_ROOT.resolve("QuestActionManager.java"));
        String ritual = slice(manager, "private static void handleRitual", "private static RitualRequirement ritualRequirement");

        assertTrue(ritual.indexOf("QuestStoryAccess.submit(player, expected)")
                < ritual.indexOf("QuestRitualPolicy.consumeAfterApplied("));
        assertTrue(manager.contains("player.sendSystemMessage("));
        assertTrue(manager.contains("player.playNotifySound("));
        assertFalse(manager.contains("level.sendParticles("));
        assertFalse(manager.contains("level.playSound("));
    }

    @Test
    void stateIsBoundedAndAllLifecycleResetsAreWired() throws IOException {
        String manager = Files.readString(SOURCE_ROOT.resolve("QuestActionManager.java"));
        String events = Files.readString(SOURCE_ROOT.resolve("QuestServerEvents.java"));
        String ledger = Files.readString(SOURCE_ROOT.resolve("BoundedPlayerState.java"));
        String session = Files.readString(SOURCE_ROOT.resolve("QuestSessionKey.java"));

        assertTrue(manager.contains("MAX_TRACKED_PLAYERS = 2_048"));
        assertTrue(manager.contains("new BoundedPlayerState<>(MAX_TRACKED_PLAYERS)"));
        assertTrue(manager.contains("QuestSessionKey.from(candidate, candidateDimension)"));
        assertTrue(session.contains("expected.recoveryEpoch()"));
        assertTrue(session.contains("expected.nodeId()"));
        assertTrue(ledger.contains("entries.size() >= capacity"));
        assertTrue(events.contains("PlayerLoggedOutEvent"));
        assertTrue(events.contains("PlayerChangedDimensionEvent"));
        assertTrue(events.contains("LivingDeathEvent"));
        assertTrue(events.contains("ServerStoppedEvent"));
        assertTrue(events.contains("QuestActionManager.clear()"));
    }

    private static String allSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException("source markers are absent");
        }
        return source.substring(from, to);
    }
}
