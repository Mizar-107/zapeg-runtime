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
        String loaded = Files.readString(SOURCE_ROOT.resolve("ServantLoadedChunks.java"));
        assertFalse(manager.contains("getAllEntities("));
        assertFalse(manager.contains("getAllLevels("));
        assertFalse(manager.contains(".getChunk("));
        assertFalse(spawn.contains(".getChunk("));
        assertTrue(loaded.contains("getChunkSource().hasChunk"));
        assertFalse(loaded.contains(".getChunk("));
        assertTrue(spawn.indexOf("loadedAndInsideBuildHeight(level, feet)")
                < spawn.indexOf("level.getBlockState(floor)"));
        assertTrue(spawn.contains("ServantLoadedChunks.spawnProbeLoaded(level, feet)"));
        assertTrue(spawn.contains("collisionChunksLoaded && level.noCollision"));
        assertTrue(manager.contains("level.getEntity(encounter.servantId())"));
        assertTrue(manager.contains("claimRecovery(encounter.encounterId())"));
    }

    @Test
    void staleEntitiesAreRejectedAtJoinAndNoGenericPlayerLookGoalExists() throws IOException {
        String events = Files.readString(SOURCE_ROOT.resolve("ServantServerEvents.java"));
        String entity = Files.readString(SOURCE_ROOT.resolve("HeraldorServant.java"));
        String loaded = Files.readString(SOURCE_ROOT.resolve("ServantLoadedChunks.java"));
        assertTrue(events.contains("EntityJoinLevelEvent"));
        assertTrue(events.contains("acceptsJoinedEntity"));
        assertFalse(entity.contains("LookAtPlayerGoal"));
        assertFalse(entity.contains("RandomStrollGoal"));
        assertFalse(entity.contains("teleportTo("));
        assertTrue(entity.contains("shouldDespawnInPeaceful"));
        assertTrue(entity.contains("isPreventingPlayerRest"));
        assertTrue(entity.contains("requiresUpdateEveryTick()"));
        assertTrue(entity.contains("pathfindingFootprintLoaded("));
        assertTrue(entity.contains("pathNodesLoaded("));
        assertTrue(loaded.contains("VANILLA_INITIAL_PATH_REGION_PADDING = 16"));
        assertTrue(loaded.contains("PathNavigation.createPath(Entity, 0)"));

        int completePreflight = entity.indexOf("private boolean canUseLoadedPursuit()");
        int vanillaCanUse = entity.indexOf("super.canUse()", completePreflight);
        int footprintCheck = entity.indexOf("pathfindingFootprintLoaded(", completePreflight);
        assertTrue(completePreflight >= 0 && footprintCheck > completePreflight);
        assertTrue(vanillaCanUse > footprintCheck,
                "complete vanilla search footprint must be resident before vanilla canUse");
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
        assertTrue(events.contains("HeraldorCommands.register(event, root ->"));
        assertTrue(events.contains("ServantCommands.attach(root)"));
        assertFalse(events.contains("HeraldorCommands.register(event);"));
    }

    @Test
    void permanentBarriersReplayIntoTheUuidCampaignAuthority() throws IOException {
        String sync = Files.readString(SOURCE_ROOT.resolve("ServantProgressionSync.java"));
        String manager = Files.readString(SOURCE_ROOT.resolve("ServantEncounterManager.java"));

        assertTrue(sync.contains("worldData.recordVictory("));
        assertTrue(manager.contains("ServantProgressionSync.replayAll(server)"));
        assertTrue(manager.contains("ServantProgressionSync.syncBarrier("));
        assertTrue(sync.contains("ServantEncounterData.get(server).liveVictories()"));
        assertTrue(sync.contains("StoryService.submitIfExpected("));
        assertTrue(sync.contains("StoryFactType.SERVANT_DEFEATED"));
        assertTrue(sync.contains("storySubject(barrier.archetype())"));
        assertTrue(sync.contains("barrier.archetype()"));
    }

    @Test
    void damagingSpecialsAreServerResolvedAndTheirTelegraphsAreShared() throws IOException {
        String entity = Files.readString(SOURCE_ROOT.resolve("HeraldorServant.java"));
        String schedule = Files.readString(SOURCE_ROOT.resolve("ServantCombatSchedule.java"));

        assertTrue(entity.contains("EntityDataSerializers.BOOLEAN"));
        assertTrue(entity.contains("level.sendParticles("));
        assertTrue(entity.contains("level.playSound("));
        assertTrue(entity.contains("level.damageSources().mobAttack(this)"));
        assertTrue(entity.contains("ServantCombatPolicy.allows(designatedTargetId"));
        assertFalse(entity.contains("Minecraft.getInstance()"));
        assertFalse(schedule.contains("Random"));
    }

    @Test
    void logoutDeathDimensionAndRestartLifecycleRemainExplicit() throws IOException {
        String events = Files.readString(SOURCE_ROOT.resolve("ServantServerEvents.java"));
        String manager = Files.readString(SOURCE_ROOT.resolve("ServantEncounterManager.java"));

        assertTrue(events.contains("PlayerLoggedOutEvent"));
        assertTrue(events.contains("PlayerChangedDimensionEvent"));
        assertTrue(events.contains("event.isWasDeath()"));
        assertTrue(events.contains("ServerStartedEvent"));
        assertTrue(manager.contains("claimRecovery("));
        assertTrue(manager.contains("MISSING_AFTER_RECOVERY"));
    }
}
