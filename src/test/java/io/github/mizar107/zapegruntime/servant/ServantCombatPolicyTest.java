package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServantCombatPolicyTest {

    @Test
    void onlyTheExactDesignatedPlayerUuidCrossesTheCombatBoundary() {
        UUID target = UUID.randomUUID();

        assertTrue(ServantCombatPolicy.allows(target, target));
        assertFalse(ServantCombatPolicy.allows(target, UUID.randomUUID()));
        assertFalse(ServantCombatPolicy.allows(target, null));
        assertFalse(ServantCombatPolicy.allows(null, target));
        assertTrue(ServantCombatPolicy.preventsRest(target, target));
        assertFalse(ServantCombatPolicy.preventsRest(target, UUID.randomUUID()));
        assertFalse(ServantCombatPolicy.DESPAWN_IN_PEACEFUL);
    }
}
