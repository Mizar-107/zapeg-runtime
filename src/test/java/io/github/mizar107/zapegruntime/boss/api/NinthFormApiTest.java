package io.github.mizar107.zapegruntime.boss.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormApiTest {

    private static final UUID ENCOUNTER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID ENTITY = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final NinthFormIdentity IDENTITY =
            new NinthFormIdentity(ENCOUNTER, OWNER, 2, false);

    @Test
    void identityRejectsNilAndAliasedAuthority() {
        UUID nil = new UUID(0L, 0L);
        assertThrows(IllegalArgumentException.class,
                () -> new NinthFormIdentity(nil, OWNER, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NinthFormIdentity(ENCOUNTER, ENCOUNTER, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NinthFormIdentity(ENCOUNTER, OWNER, -1, false));
    }

    @Test
    void combatPhasesOnlyAdvanceAlongTheDesignedEdges() {
        assertTrue(NinthFormPhase.PRELUDE.canAdvanceTo(NinthFormPhase.FIRST));
        assertTrue(NinthFormPhase.FIRST.canAdvanceTo(NinthFormPhase.INTERLUDE));
        assertTrue(NinthFormPhase.INTERLUDE.canAdvanceTo(NinthFormPhase.FINAL));
        assertTrue(NinthFormPhase.FINAL.canAdvanceTo(NinthFormPhase.BANISHED));
        assertFalse(NinthFormPhase.FIRST.canAdvanceTo(NinthFormPhase.FINAL));
        assertFalse(NinthFormPhase.BANISHED.canAdvanceTo(NinthFormPhase.BANISHED));
    }

    @Test
    void snapshotsEnforceLoadedCombatBounds() {
        NinthFormCombatSnapshot snapshot = new NinthFormCombatSnapshot(
                IDENTITY, ENTITY, NinthFormPhase.FIRST, "minecraft:overworld",
                10.0D, 64.0D, -10.0D, 450.0D, 900.0D, 8,
                new NinthFormCombatSnapshot.CombatState(0b011, 7L, "keel_sweep", 18), 42L);
        assertEquals(8, snapshot.participantCount());
        assertThrows(IllegalArgumentException.class, () -> new NinthFormCombatSnapshot(
                IDENTITY, ENTITY, NinthFormPhase.FIRST, "minecraft:overworld",
                10.0D, 64.0D, -10.0D, 901.0D, 900.0D, 1,
                new NinthFormCombatSnapshot.CombatState(0, 0L, "idle", 0), 42L));
        assertThrows(IllegalArgumentException.class, () -> new NinthFormCombatSnapshot(
                IDENTITY, ENTITY, NinthFormPhase.BANISHED, "minecraft:overworld",
                10.0D, 64.0D, -10.0D, 1.0D, 900.0D, 1,
                new NinthFormCombatSnapshot.CombatState(0b111, 10L, "idle", 0), 42L));
    }

    @Test
    void gatewayValuesCannotClaimSuccessWithoutAnEntity() {
        assertThrows(IllegalArgumentException.class, () -> new NinthFormEntityGateway.SpawnResult(
                NinthFormEntityGateway.Status.APPLIED, Optional.empty(), "spawned"));
        assertThrows(IllegalArgumentException.class, () -> new NinthFormEntityGateway.SpawnResult(
                NinthFormEntityGateway.Status.NOT_LOADED, Optional.of(ENTITY), "not loaded"));
        NinthFormEntityGateway.SpawnRequest request = new NinthFormEntityGateway.SpawnRequest(
                IDENTITY, ENTITY, "minecraft:overworld", 0.0D, 70.0D, 0.0D,
                NinthFormPhase.FINAL, 4, 1.75D, 1.30D,
                new NinthFormCombatSnapshot.CombatState(0b111, 4L, "idle", 0));
        assertEquals(4, request.participantCount());
    }

    @Test
    void signalsBindKindPhaseAndOwnerCredit() {
        NinthFormCombatSignal signal = new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                IDENTITY,
                ENTITY,
                NinthFormPhase.FIRST,
                OWNER,
                99L);
        assertEquals(OWNER, signal.creditedPlayerId());
        assertThrows(IllegalArgumentException.class, () -> new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.DEFEATED,
                IDENTITY,
                ENTITY,
                NinthFormPhase.FINAL,
                OWNER,
                100L));
        assertThrows(IllegalArgumentException.class, () -> new NinthFormCombatSignal(
                NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                IDENTITY,
                ENTITY,
                NinthFormPhase.FIRST,
                UUID.randomUUID(),
                100L));
    }
}
