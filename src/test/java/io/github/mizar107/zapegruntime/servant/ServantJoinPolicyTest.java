package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServantJoinPolicyTest {

    @Test
    void acceptsOnlyTheExactPersistedEntityAndRejectsALateStaleTwin() {
        ServantEncounter persisted = new ServantEncounter(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "minecraft:overworld",
                false,
                12_345L,
                true,
                ServantArchetype.BINDER);
        assertTrue(accepts(persisted, persisted.servantId()));
        assertFalse(accepts(persisted, UUID.randomUUID()),
                "old entity loading after replacement must be rejected");
        assertFalse(ServantJoinPolicy.accepts(
                null,
                persisted.encounterId(),
                persisted.targetId(),
                persisted.servantId(),
                persisted.dimension(),
                persisted.rehearsal(),
                persisted.deadlineGameTime(),
                persisted.archetype()));
        assertFalse(ServantJoinPolicy.accepts(
                persisted,
                persisted.encounterId(),
                persisted.targetId(),
                persisted.servantId(),
                persisted.dimension(),
                persisted.rehearsal(),
                persisted.deadlineGameTime(),
                ServantArchetype.HERALD));
        assertFalse(ServantJoinPolicy.accepts(
                persisted,
                persisted.encounterId(),
                persisted.targetId(),
                persisted.servantId(),
                "minecraft:the_nether",
                persisted.rehearsal(),
                persisted.deadlineGameTime()));
    }

    private static boolean accepts(ServantEncounter persisted, UUID entityId) {
        return ServantJoinPolicy.accepts(
                persisted,
                persisted.encounterId(),
                persisted.targetId(),
                entityId,
                persisted.dimension(),
                persisted.rehearsal(),
                persisted.deadlineGameTime(),
                persisted.archetype());
    }
}
