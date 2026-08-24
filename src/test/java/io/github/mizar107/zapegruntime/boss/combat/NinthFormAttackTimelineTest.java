package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormAttackTimelineTest {

    private static final UUID ENCOUNTER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void everyAttackHasLongTelegraphThenActiveAndRecovery() {
        assertEquals(6, NinthFormAttack.values().length);
        for (NinthFormAttack attack : NinthFormAttack.values()) {
            assertTrue(attack.windupTicks() >= 24);
            assertTrue(attack.activeTicks() > 0);
            assertTrue(attack.recoveryTicks() > 0);
            assertEquals(
                    NinthFormAttack.AttackWindow.WINDUP,
                    attack.windowAt(attack.windupTicks() - 1));
            assertEquals(
                    NinthFormAttack.AttackWindow.ACTIVE,
                    attack.windowAt(attack.windupTicks()));
            assertEquals(
                    NinthFormAttack.AttackWindow.RECOVERY,
                    attack.windowAt(attack.windupTicks() + attack.activeTicks()));
            assertEquals(
                    NinthFormAttack.AttackWindow.COMPLETE,
                    attack.windowAt(attack.totalTicks()));
        }
    }

    @Test
    void wakeChargeAndNinefoldGazeArePhaseTwoOnly() {
        assertFalse(NinthFormAttack.WAKE_CHARGE.allowedIn(NinthFormPhase.FIRST));
        assertFalse(NinthFormAttack.NINEFOLD_GAZE.allowedIn(NinthFormPhase.FIRST));
        assertTrue(NinthFormAttack.WAKE_CHARGE.allowedIn(NinthFormPhase.FINAL));
        assertTrue(NinthFormAttack.NINEFOLD_GAZE.allowedIn(NinthFormPhase.FINAL));
    }

    @Test
    void selectionIsStablePhaseAwareAndNeverImmediatelyRepeats() {
        for (NinthFormPhase phase : new NinthFormPhase[] {
                NinthFormPhase.FIRST, NinthFormPhase.FINAL}) {
            String previous = "idle";
            for (long cycle = 0L; cycle < 200L; cycle++) {
                NinthFormAttack first =
                        NinthFormAttackSelector.select(ENCOUNTER, phase, cycle, previous);
                NinthFormAttack second =
                        NinthFormAttackSelector.select(ENCOUNTER, phase, cycle, previous);
                assertEquals(first, second);
                assertNotEquals(previous, first.serializedName());
                assertTrue(first.allowedIn(phase));
                previous = first.serializedName();
            }
        }
    }
}
