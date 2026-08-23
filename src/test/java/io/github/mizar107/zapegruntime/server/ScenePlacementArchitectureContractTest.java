package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenePlacementArchitectureContractTest {

    private static final Path SOURCE_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107",
            "zapegruntime", "server");

    @Test
    void rawPlacementQueriesExistOnlyBehindTheLoadedChunkGateway()
            throws IOException {
        String placement = Files.readString(SOURCE_ROOT.resolve("ScenePlacement.java"));
        String gateway = Files.readString(SOURCE_ROOT.resolve("LoadedSceneQueries.java"));

        assertFalse(placement.contains("level.clip("));
        assertFalse(placement.contains("level.noCollision("));
        assertEquals(1, occurrences(gateway, "level.clip("));
        assertEquals(1, occurrences(gateway, "level.noCollision("));
        assertTrue(gateway.contains("level.getChunkSource().hasChunk("));
        assertFalse(gateway.contains(".getChunk("));
    }

    @Test
    void timelineAndLegacyPlacementEntropyRemainSeparate() throws IOException {
        String placement = Files.readString(SOURCE_ROOT.resolve("ScenePlacement.java"));

        assertTrue(placement.contains("findSeeded("));
        assertTrue(placement.contains("seededCandidateOrder("));
        assertTrue(placement.contains("seededHorizonAzimuth("));
        assertTrue(placement.contains("player.getRandom().nextInt("));
        assertTrue(placement.contains("player.getRandom().nextDouble("));
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
