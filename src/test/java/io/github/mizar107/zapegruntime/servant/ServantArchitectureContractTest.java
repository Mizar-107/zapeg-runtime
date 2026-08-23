package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServantArchitectureContractTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "io", "github",
            "mizar107", "zapegruntime", "servant");

    @Test
    void recoveryHasNoGlobalEntityScanOrForcedChunkLoad() throws IOException {
        String manager = Files.readString(SOURCE_ROOT.resolve("ServantEncounterManager.java"));
        String spawn = Files.readString(SOURCE_ROOT.resolve("ServantSpawnPolicy.java"));
        assertFalse(manager.contains("getAllEntities("));
        assertFalse(manager.contains("getAllLevels("));
        assertFalse(manager.contains(".getChunk("));
        assertFalse(spawn.contains(".getChunk("));
        assertTrue(spawn.contains("hasChunkAt"));
        assertTrue(manager.contains("level.getEntity(encounter.servantId())"));
        assertTrue(manager.contains("claimRecovery(encounter.encounterId())"));
    }

    @Test
    void staleEntitiesAreRejectedAtJoinAndNoGenericPlayerLookGoalExists() throws IOException {
        String events = Files.readString(SOURCE_ROOT.resolve("ServantServerEvents.java"));
        String entity = Files.readString(SOURCE_ROOT.resolve("HeraldorServant.java"));
        assertTrue(events.contains("EntityJoinLevelEvent"));
        assertTrue(events.contains("acceptsJoinedEntity"));
        assertFalse(entity.contains("LookAtPlayerGoal"));
        assertTrue(entity.contains("shouldDespawnInPeaceful"));
        assertTrue(entity.contains("isPreventingPlayerRest"));
    }
}
