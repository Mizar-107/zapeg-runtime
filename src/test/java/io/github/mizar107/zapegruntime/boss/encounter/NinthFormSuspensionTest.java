package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NinthFormSuspensionTest {

    @Test
    void exactSuspendDiscardsOnlyTheBoundEntityAndMismatchHasNoCollateral() {
        NinthFormEncounterData data = new NinthFormEncounterData();
        NinthFormEncounter encounter = new NinthFormEncounter(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                false,
                ResourceLocation.tryBuild("zapeg_runtime", "heraldor"),
                1,
                "a".repeat(64),
                0L,
                "minecraft:overworld",
                0,
                64,
                0,
                NinthFormPhase.PRELUDE,
                NinthFormEncounter.Lifecycle.PREPARED,
                1,
                1.0D,
                1.0D,
                new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0),
                NinthFormCombatSnapshot.VitalState.pristine(),
                0L);
        data.begin(encounter);
        data.activate(encounter.identity(), encounter.entityId());
        CountingGateway gateway = new CountingGateway();
        NinthFormCombatSignal exact = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.SUSPENDED,
                encounter.identity(),
                encounter.entityId(),
                NinthFormPhase.PRELUDE,
                encounter.targetId(),
                1L);
        assertEquals(
                NinthFormEncounterData.MutationResult.APPLIED,
                NinthFormEncounterManager.suspendFromSignal(data, gateway, exact));
        assertEquals(1, gateway.discards);
        assertEquals(encounter.entityId(), gateway.lastDiscarded);

        NinthFormIdentity mismatchIdentity = new NinthFormIdentity(
                UUID.randomUUID(), encounter.targetId(), 0, false);
        NinthFormCombatSignal mismatch = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.SUSPENDED,
                mismatchIdentity,
                UUID.randomUUID(),
                NinthFormPhase.PRELUDE,
                encounter.targetId(),
                2L);
        assertEquals(
                NinthFormEncounterData.MutationResult.IDENTITY_MISMATCH,
                NinthFormEncounterManager.suspendFromSignal(data, gateway, mismatch));
        assertEquals(1, gateway.discards, "mismatch must not touch any entity");
    }

    private static final class CountingGateway implements NinthFormEntityGateway {
        private int discards;
        private UUID lastDiscarded;

        @Override
        public SpawnResult spawnLoaded(SpawnRequest request) {
            return new SpawnResult(Status.FAILED, Optional.empty(), "unused");
        }

        @Override
        public Optional<NinthFormCombatSnapshot> observeLoaded(
                NinthFormIdentity identity, UUID entityId) {
            return Optional.empty();
        }

        @Override
        public ControlResult transitionLoaded(
                NinthFormIdentity identity,
                UUID entityId,
                NinthFormPhase expected,
                NinthFormPhase next) {
            return new ControlResult(Status.FAILED, "unused");
        }

        @Override
        public ControlResult suspendLoaded(NinthFormIdentity identity, UUID entityId) {
            return new ControlResult(Status.FAILED, "unused");
        }

        @Override
        public ControlResult discardLoaded(NinthFormIdentity identity, UUID entityId) {
            discards++;
            lastDiscarded = entityId;
            return new ControlResult(Status.APPLIED, "discarded");
        }
    }
}
