package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NinthFormEncounterDataTest {

    private static final ResourceLocation CAMPAIGN =
            ResourceLocation.tryBuild("zapeg_runtime", "heraldor");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void phaseAndDefeatProofsAreAtomicImmutableAndReplaySafe() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter encounter = encounter(false, NinthFormPhase.PRELUDE);
        assertEquals(NinthFormEncounterData.BeginStatus.STARTED, data.begin(encounter).status());
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.activate(encounter.identity(), encounter.entityId()));
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.advanceActivePhase(
                        encounter.identity(),
                        encounter.entityId(),
                        NinthFormPhase.PRELUDE,
                        NinthFormPhase.FIRST));
        NinthFormEncounter first = data.findByEncounter(encounter.encounterId()).orElseThrow();
        assertEquals(
                NinthFormEncounterData.MutationResult.STATE_MISMATCH,
                data.advanceActivePhase(
                        first.identity(),
                        first.entityId(),
                        NinthFormPhase.FIRST,
                        NinthFormPhase.INTERLUDE),
                "only a combat proof may cross the first-phase barrier");
        NinthFormCombatSignal phaseSignal = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                first.identity(),
                first.entityId(),
                NinthFormPhase.FIRST,
                first.targetId(),
                100L);
        assertEquals(
                NinthFormEncounterData.ProofResult.RECORDED,
                data.recordPhaseCompletion(phaseSignal));
        assertEquals(1, data.immutableBarriers().size());
        assertEquals(NinthFormPhase.INTERLUDE,
                data.findByEncounter(encounter.encounterId()).orElseThrow().phase());
        NinthFormEncounter interludeObservation =
                data.findByEncounter(encounter.encounterId()).orElseThrow();
        assertEquals(0b111, interludeObservation.combatState().brokenPointMask());
        assertEquals("idle", interludeObservation.combatState().attackId());
        assertEquals(0, interludeObservation.combatState().attackTick());
        assertEquals(0.0D, interludeObservation.vitalState().prowHealthFraction());
        assertEquals(0.0D, interludeObservation.vitalState().portHealthFraction());
        assertEquals(0.0D, interludeObservation.vitalState().starboardHealthFraction());
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.storeSnapshot(new NinthFormCombatSnapshot(
                        interludeObservation.identity(),
                        interludeObservation.entityId(),
                        NinthFormPhase.INTERLUDE,
                        interludeObservation.dimensionId(),
                        interludeObservation.arenaX() + 0.5D,
                        interludeObservation.arenaY(),
                        interludeObservation.arenaZ() + 0.5D,
                        1000.0D,
                        1000.0D,
                        interludeObservation.participantCount(),
                        interludeObservation.combatState(),
                        interludeObservation.vitalState(),
                        101L)),
                "post-signal INTERLUDE observation must match durable authority");
        assertEquals(
                NinthFormEncounterData.ProofResult.REPLAYED,
                data.recordPhaseCompletion(phaseSignal));

        NinthFormEncounter interlude = data.findByEncounter(encounter.encounterId()).orElseThrow();
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.advanceActivePhase(
                        interlude.identity(),
                        interlude.entityId(),
                        NinthFormPhase.INTERLUDE,
                        NinthFormPhase.FINAL));
        NinthFormEncounter finale = data.findByEncounter(encounter.encounterId()).orElseThrow();
        NinthFormCombatSignal defeatSignal = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.DEFEATED,
                finale.identity(),
                finale.entityId(),
                NinthFormPhase.BANISHED,
                finale.targetId(),
                200L);
        assertEquals(
                NinthFormEncounterData.ProofResult.RECORDED,
                data.recordDefeat(defeatSignal));
        assertTrue(data.activeEncounters().isEmpty());
        assertEquals(2, data.immutableBarriers().size());
        assertEquals(
                NinthFormEncounterData.ProofResult.REPLAYED,
                data.recordDefeat(defeatSignal));
        assertThrows(
                UnsupportedOperationException.class,
                () -> data.immutableBarriers().add(data.immutableBarriers().get(0)));

        NinthFormEncounterData reloaded =
                NinthFormEncounterData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.schemaStatus().writable());
        assertEquals(data.immutableBarriers(), reloaded.immutableBarriers());
    }

    @Test
    void rehearsalsNeverCreateStoryBarriers() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter encounter = beginFirst(data, true);
        NinthFormCombatSignal phase = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                encounter.identity(),
                encounter.entityId(),
                NinthFormPhase.FIRST,
                encounter.targetId(),
                1L);
        assertEquals(NinthFormEncounterData.ProofResult.REHEARSAL, data.recordPhaseCompletion(phase));
        NinthFormEncounter interlude = data.findByEncounter(encounter.encounterId()).orElseThrow();
        data.advanceActivePhase(
                interlude.identity(), interlude.entityId(), NinthFormPhase.INTERLUDE, NinthFormPhase.FINAL);
        NinthFormEncounter finale = data.findByEncounter(encounter.encounterId()).orElseThrow();
        NinthFormCombatSignal defeat = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.DEFEATED,
                finale.identity(),
                finale.entityId(),
                NinthFormPhase.BANISHED,
                finale.targetId(),
                2L);
        assertEquals(NinthFormEncounterData.ProofResult.REHEARSAL, data.recordDefeat(defeat));
        assertTrue(data.activeEncounters().isEmpty());
        assertTrue(data.immutableBarriers().isEmpty());
    }

    @Test
    void suspendedRecoveryRotatesGenerationAndRejectsTheStaleEntity() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter encounter = beginFirst(data, false);
        data.suspend(encounter.identity(), encounter.entityId());
        assertFalse(data.acceptsEntity(encounter.identity(), encounter.entityId()));

        UUID replacement = UUID.randomUUID();
        NinthFormEncounterData.RotationResult result =
                data.rotateGeneration(encounter.encounterId(), replacement);
        assertEquals(NinthFormEncounterData.RotationStatus.ROTATED, result.status());
        NinthFormEncounter rotated = result.encounter();
        assertEquals(encounter.generation() + 1, rotated.generation());
        assertNotEquals(encounter.entityId(), rotated.entityId());
        assertFalse(data.acceptsEntity(encounter.identity(), encounter.entityId()));
        assertTrue(data.acceptsEntity(rotated.identity(), rotated.entityId()));

        NinthFormEncounterData reloaded =
                NinthFormEncounterData.load(data.save(new CompoundTag()));
        assertEquals(rotated, reloaded.findByEncounter(encounter.encounterId()).orElseThrow());
    }

    @Test
    void normalizedParentAndAllWeakPointsRoundTripWithoutRestartHealing() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter encounter = beginFirst(data, false);
        NinthFormCombatSnapshot snapshot = new NinthFormCombatSnapshot(
                encounter.identity(),
                encounter.entityId(),
                encounter.phase(),
                encounter.dimensionId(),
                encounter.arenaX() + 3.0D,
                encounter.arenaY(),
                encounter.arenaZ() + 2.0D,
                500.0D,
                1000.0D,
                encounter.participantCount(),
                new NinthFormCombatSnapshot.CombatState(0b001, 9L, "undertow", 12),
                new NinthFormCombatSnapshot.VitalState(0.5D, 0.0D, 0.4D, 0.7D),
                99L);
        assertEquals(NinthFormEncounterData.MutationResult.APPLIED, data.storeSnapshot(snapshot));
        NinthFormEncounterData reloaded =
                NinthFormEncounterData.load(data.save(new CompoundTag()));
        NinthFormEncounter restored = reloaded.activeFor(encounter.targetId()).orElseThrow();
        assertEquals(snapshot.combatState(), restored.combatState());
        assertEquals(snapshot.vitalState(), restored.vitalState());
        assertEquals(snapshot.vitalState(), restored.spawnRequest().vitalState());
    }

    @Test
    void restartAfterFirstPhaseKeepsCheckpointProofVitalityAndRejectsOldGeneration() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter first = beginFirst(data, false);
        NinthFormCombatSnapshot snapshot = new NinthFormCombatSnapshot(
                first.identity(),
                first.entityId(),
                NinthFormPhase.FIRST,
                first.dimensionId(),
                first.arenaX() + 1.0D,
                first.arenaY(),
                first.arenaZ() + 1.0D,
                600.0D,
                1000.0D,
                first.participantCount(),
                new NinthFormCombatSnapshot.CombatState(0b010, 11L, "anchorfall", 7),
                new NinthFormCombatSnapshot.VitalState(0.6D, 0.8D, 0.0D, 0.3D),
                70L);
        data.storeSnapshot(snapshot);
        NinthFormCombatSignal phaseSignal = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                first.identity(),
                first.entityId(),
                NinthFormPhase.FIRST,
                first.targetId(),
                71L);
        assertEquals(NinthFormEncounterData.ProofResult.RECORDED,
                data.recordPhaseCompletion(phaseSignal));
        NinthFormEncounter interlude = data.findByEncounter(first.encounterId()).orElseThrow();
        data.suspend(interlude.identity(), interlude.entityId());

        NinthFormEncounterData reloaded =
                NinthFormEncounterData.load(data.save(new CompoundTag()));
        NinthFormEncounter checkpoint = reloaded.findByEncounter(first.encounterId()).orElseThrow();
        assertEquals(NinthFormPhase.INTERLUDE, checkpoint.phase());
        assertEquals(snapshot.vitalState().parentHealthFraction(),
                checkpoint.vitalState().parentHealthFraction());
        assertEquals(0b111, checkpoint.combatState().brokenPointMask());
        assertEquals("idle", checkpoint.combatState().attackId());
        assertEquals(0, checkpoint.combatState().attackTick());
        assertEquals(0.0D, checkpoint.vitalState().prowHealthFraction());
        assertEquals(0.0D, checkpoint.vitalState().portHealthFraction());
        assertEquals(0.0D, checkpoint.vitalState().starboardHealthFraction());
        NinthFormBarrier proof = reloaded.barrierFor(
                        first.encounterId(), NinthFormBarrier.Kind.PHASE_ONE_COMPLETED)
                .orElseThrow();
        assertEquals(first.entityId(), proof.entityId());
        assertEquals(first.generation(), proof.generation());

        UUID replacement = UUID.randomUUID();
        NinthFormEncounter rotated = reloaded
                .rotateGeneration(first.encounterId(), replacement)
                .encounter();
        assertEquals(first.generation() + 1, rotated.generation());
        assertEquals(checkpoint.vitalState(), rotated.spawnRequest().vitalState());
        assertEquals(checkpoint.combatState(), rotated.spawnRequest().combatState());
        assertFalse(reloaded.acceptsEntity(first.identity(), first.entityId()));
        assertTrue(reloaded.acceptsEntity(rotated.identity(), rotated.entityId()));
        assertEquals(
                NinthFormEncounterData.ProofResult.REPLAYED,
                reloaded.recordPhaseCompletion(phaseSignal),
                "old proof is an inert replay, never a current-generation transition");
    }

    @Test
    void futureAndCorruptRootsAreBytePreservedAndReadOnly() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 2);
        future.putString("FutureOpaque", "preserve-me");
        NinthFormEncounterData futureData = NinthFormEncounterData.load(future);
        assertEquals(NinthFormEncounterData.DataHealth.UNSUPPORTED,
                futureData.schemaStatus().health());
        assertEquals(future, futureData.save(new CompoundTag()));

        NinthFormEncounterData valid = new NinthFormEncounterData();
        valid.begin(encounter(false, NinthFormPhase.PRELUDE));
        CompoundTag corrupt = valid.save(new CompoundTag());
        corrupt.getList("Active", 10).getCompound(0).putString("Unknown", "poison");
        NinthFormEncounterData corruptData = NinthFormEncounterData.load(corrupt);
        assertEquals(NinthFormEncounterData.DataHealth.CORRUPT,
                corruptData.schemaStatus().health());
        assertEquals(corrupt, corruptData.save(new CompoundTag()));
        assertEquals(
                NinthFormEncounterData.BeginStatus.DATA_UNAVAILABLE,
                corruptData.begin(encounter(true, NinthFormPhase.PRELUDE)).status());
    }

    @Test
    void capacitiesRefuseWithoutEvictingImmutableHistory() {
        CompoundTag full = terminalRoot(NinthFormEncounterData.MAX_IMMUTABLE_BARRIERS / 2);
        NinthFormEncounterData data = NinthFormEncounterData.load(full);
        assertTrue(data.schemaStatus().writable(), data.schemaStatus().detail());
        assertEquals(NinthFormEncounterData.MAX_IMMUTABLE_BARRIERS,
                data.immutableBarriers().size());
        assertEquals(
                NinthFormEncounterData.BeginStatus.BARRIER_CAPACITY_EXHAUSTED,
                data.begin(encounter(false, NinthFormPhase.PRELUDE)).status());
        assertEquals(
                NinthFormEncounterData.BeginStatus.STARTED,
                data.begin(encounter(true, NinthFormPhase.PRELUDE)).status());

        NinthFormEncounterData active = new NinthFormEncounterData();
        for (int index = 0; index < NinthFormEncounterData.MAX_ACTIVE_ENCOUNTERS; index++) {
            assertEquals(
                    NinthFormEncounterData.BeginStatus.STARTED,
                    active.begin(encounter(true, NinthFormPhase.PRELUDE)).status());
        }
        assertEquals(
                NinthFormEncounterData.BeginStatus.ACTIVE_CAPACITY_EXHAUSTED,
                active.begin(encounter(true, NinthFormPhase.PRELUDE)).status());
    }

    private static NinthFormEncounter encounter(boolean rehearsal, NinthFormPhase phase) {
        return new NinthFormEncounter(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                rehearsal,
                CAMPAIGN,
                1,
                FINGERPRINT,
                4L,
                "minecraft:overworld",
                100,
                64,
                100,
                phase,
                NinthFormEncounter.Lifecycle.PREPARED,
                1,
                1.0D,
                1.0D,
                new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0),
                NinthFormCombatSnapshot.VitalState.pristine(),
                0L);
    }

    private static NinthFormEncounter beginFirst(
            NinthFormEncounterData data, boolean rehearsal) {
        NinthFormEncounter encounter = encounter(rehearsal, NinthFormPhase.PRELUDE);
        assertEquals(NinthFormEncounterData.BeginStatus.STARTED, data.begin(encounter).status());
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.activate(encounter.identity(), encounter.entityId()));
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                data.advanceActivePhase(
                        encounter.identity(),
                        encounter.entityId(),
                        NinthFormPhase.PRELUDE,
                        NinthFormPhase.FIRST));
        return data.findByEncounter(encounter.encounterId()).orElseThrow();
    }

    private static CompoundTag terminalRoot(int completedEncounters) {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", NinthFormEncounterData.CURRENT_SCHEMA_VERSION);
        root.put("Active", new ListTag());
        ListTag barriers = new ListTag();
        for (long index = 1; index <= completedEncounters; index++) {
            UUID encounter = new UUID(1L, index);
            UUID target = new UUID(2L, index);
            UUID entity = new UUID(3L, index);
            barriers.add(new NinthFormBarrier(
                    new UUID(4L, index),
                    encounter,
                    target,
                    entity,
                    0,
                    CAMPAIGN,
                    1,
                    FINGERPRINT,
                    0L,
                    NinthFormBarrier.Kind.PHASE_ONE_COMPLETED).save());
            barriers.add(new NinthFormBarrier(
                    new UUID(5L, index),
                    encounter,
                    target,
                    entity,
                    0,
                    CAMPAIGN,
                    1,
                    FINGERPRINT,
                    0L,
                    NinthFormBarrier.Kind.DEFEATED).save());
        }
        root.put("Barriers", barriers);
        return root;
    }
}
