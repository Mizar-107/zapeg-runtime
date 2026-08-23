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

    @Test
    void forgeVictoryEventIsDocumentedAsAdvisoryAndDeduplicated() throws IOException {
        String event = Files.readString(SOURCE_ROOT.resolve("ServantVictoryEvent.java"));
        assertTrue(event.contains("gameplay must not"));
        assertTrue(event.contains("deduplicate by"));
        assertTrue(event.contains("liveVictories()"));
        assertFalse(event.contains("Posted once"));
    }

    @Test
    void integrationWiresRegistryAndExactlyOneSharedHeraldorRoot() throws IOException {
        Path runtimeRoot = SOURCE_ROOT.getParent();
        String mod = Files.readString(runtimeRoot.resolve("ZapeGRuntime.java"));
        String events = Files.readString(
                runtimeRoot.resolve("server").resolve("SceneServerEvents.java"));

        assertTrue(mod.contains("ServantEntities.register(modBus)"));
        assertTrue(events.contains(
                "HeraldorCommands.register(event, ServantCommands::attach)"));
        assertFalse(events.contains("HeraldorCommands.register(event);"));
    }

    @Test
    void permanentBarriersReplayIntoTheUuidCampaignAuthority() throws IOException {
        String sync = Files.readString(SOURCE_ROOT.resolve("ServantProgressionSync.java"));
        String manager = Files.readString(SOURCE_ROOT.resolve("ServantEncounterManager.java"));

        assertTrue(sync.contains("ServantEncounterData.get(server).liveVictories()"));
        assertTrue(sync.contains("worldData.recordVictory("));
        assertTrue(manager.contains("ServantProgressionSync.replayAll(server)"));
        assertTrue(manager.contains("ServantProgressionSync.syncBarrier("));
    }
}
