package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import org.junit.jupiter.api.Test;

class NinthFormRecoveryPolicyTest {

    @Test
    void recoveryPreservesDurableCursorButRestartsTheWholeWindup() {
        NinthFormCombatSnapshot.CombatState recovered =
                NinthFormRecoveryPolicy.restartAtWindup(
                        new NinthFormCombatSnapshot.CombatState(
                                0b011, 19L, "drowned_broadside", 47));
        assertEquals(0b011, recovered.brokenPointMask());
        assertEquals(19L, recovered.attackCycle());
        assertEquals("drowned_broadside", recovered.attackId());
        assertEquals(0, recovered.attackTick());
    }
}
