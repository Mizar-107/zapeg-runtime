package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NinthFormArchitectureContractTest {

    private static final Path ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");
    private static final Path ENCOUNTER = ROOT.resolve("boss").resolve("encounter");

    @Test
    void entityOperationsUseOnlyTheGatewayAndNeverForceChunksOrMutateTerrain()
            throws IOException {
        String manager = Files.readString(ENCOUNTER.resolve("NinthFormEncounterManager.java"));
        String arena = Files.readString(ENCOUNTER.resolve("NinthFormArenaPolicy.java"));
        assertTrue(manager.contains("NinthFormGatewayRegistry.current(server)"));
        assertTrue(manager.contains("level.getChunkSource()::hasChunk"));
        assertFalse(manager.contains(".getChunk("));
        assertFalse(manager.contains("getAllEntities("));
        assertFalse(manager.contains("setChunkForced"));
        assertFalse(manager.contains("addRegionTicket"));
        assertFalse(manager.contains("setBlock("));
        assertFalse(manager.contains("destroyBlock("));
        assertTrue(arena.contains("ARENA_RADIUS = 48"));
        assertTrue(arena.contains("MIN_BOSS_SEPARATION = 128"));
        assertTrue(arena.contains("MAX_LOADED_BOSSES = 4"));
    }

    @Test
    void lifecycleSuspendsOnEveryRequiredTargetBoundaryAndQueuesStoryWork()
            throws IOException {
        String events = Files.readString(ENCOUNTER.resolve("NinthFormServerEvents.java"));
        String manager = Files.readString(ENCOUNTER.resolve("NinthFormEncounterManager.java"));
        assertTrue(events.contains("PlayerLoggedOutEvent"));
        assertTrue(events.contains("PlayerChangedDimensionEvent"));
        assertTrue(events.contains("event.isWasDeath()"));
        assertTrue(events.contains("ServerStartedEvent"));
        assertTrue(events.contains("ServerStoppingEvent"));
        assertTrue(events.contains("StoryAdvancedEvent"));
        assertTrue(manager.contains("server.execute(() -> onCombatSignal"));
        assertTrue(manager.contains("queueTarget(server, signal.identity().targetId())"));
        assertTrue(manager.contains("NinthFormArenaPolicy.contains("));
        assertFalse(manager.substring(
                        manager.indexOf("public static void onCombatSignal"),
                        manager.indexOf("public static boolean acceptsEntity"))
                .contains("StoryService.submit"));
    }

    @Test
    void combatIntegrationSeamRequiresLifecycleBindingSignalSinkAndExactJoinGate()
            throws IOException {
        String registry = Files.readString(ENCOUNTER.resolve("NinthFormGatewayRegistry.java"));
        String manager = Files.readString(ENCOUNTER.resolve("NinthFormEncounterManager.java"));
        assertTrue(registry.contains("install(\n            MinecraftServer server"));
        assertTrue(registry.contains("uninstall(\n            MinecraftServer server"));
        assertTrue(registry.contains("NinthFormEncounterManager#onCombatSignal"));
        assertTrue(registry.contains("NinthFormEncounterManager#acceptsEntity"));
        assertTrue(manager.contains("NinthFormEncounterData.get(server).acceptsEntity(identity, entityId)"));
    }

    @Test
    void commandSubtreeIsStandaloneAndRewardHasNoPhysicalLoot() throws IOException {
        String commands = Files.readString(ENCOUNTER.resolve("NinthFormCommands.java"));
        String globalCommands = Files.readString(ROOT.resolve("server").resolve("HeraldorCommands.java"));
        String reward = Files.readString(ENCOUNTER.resolve("NinthFormRewardService.java"));
        String advancement = Files.readString(Path.of(
                "src", "main", "resources", "data", "zapeg_runtime", "advancements",
                "heraldor", "banish_ninth_form.json"));
        assertTrue(commands.contains("EntityArgument.player()"));
        assertFalse(globalCommands.contains("NinthFormCommands"));
        assertTrue(reward.contains("getAdvancements().award"));
        assertFalse(reward.contains("ItemStack"));
        assertFalse(reward.contains("spawnAtLocation"));
        assertTrue(advancement.contains("\"show_toast\": true"));
        assertFalse(advancement.contains("\"rewards\""));
    }

    @Test
    void schemaNameAndHardCapsAreExplicitAndHistoryNeverEvicts() throws IOException {
        String data = Files.readString(ENCOUNTER.resolve("NinthFormEncounterData.java"));
        assertTrue(data.contains("zapeg_runtime_ninth_form"));
        assertTrue(data.contains("MAX_ACTIVE_ENCOUNTERS = 32"));
        assertTrue(data.contains("MAX_IMMUTABLE_BARRIERS = 4_096"));
        assertFalse(data.contains("removeEldestEntry"));
        assertFalse(data.contains("barriersByFact.remove"));
        assertTrue(data.contains("return preservedRoot.copy()"));
    }
}
