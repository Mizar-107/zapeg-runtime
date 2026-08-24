package io.github.mizar107.zapegruntime.boss.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.AttackTiming;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.AttackWindow;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormRenderState.RenderMode;
import org.junit.jupiter.api.Test;

class NinthFormRenderStateTest {

    @Test
    void presentationTimelinesExactlyMatchTheCombatContract() {
        assertTiming(AttackTiming.KEEL_SWEEP, 30, 10, 26);
        assertTiming(AttackTiming.ANCHORFALL, 36, 8, 30);
        assertTiming(AttackTiming.UNDERTOW, 28, 20, 28);
        assertTiming(AttackTiming.DROWNED_BROADSIDE, 42, 8, 30);
        assertTiming(AttackTiming.WAKE_CHARGE, 32, 16, 30);
        assertTiming(AttackTiming.NINEFOLD_GAZE, 48, 18, 34);
    }

    @Test
    void unknownOrIdleAttackNeverInventsATelegraph() {
        for (String attack : new String[] {null, "idle", "future_attack", ""}) {
            var state = NinthFormRenderState.resolve(
                    NinthFormPhase.FIRST, attack, 17, 0, 42.0F);
            assertEquals(AttackTiming.IDLE, state.attack());
            assertEquals(AttackWindow.IDLE, state.window());
            assertFalse(state.telegraphing());
            assertEquals(0.0F, state.windupProgress());
        }
    }

    @Test
    void phaseAndBreakMaskDriveOnlyBoundedClientState() {
        var first = NinthFormRenderState.resolve(
                NinthFormPhase.FIRST, "keel_sweep", 29, 0b101, 19.0F);
        assertEquals(RenderMode.CUTOUT, first.renderMode());
        assertTrue(first.telegraphing());
        assertFalse(first.prowAlive());
        assertTrue(first.portAlive());
        assertFalse(first.starboardAlive());
        assertTrue(first.keelExposed());

        var interlude = NinthFormRenderState.resolve(
                NinthFormPhase.INTERLUDE, "idle", 0, 0, 19.0F);
        assertEquals(RenderMode.TRANSLUCENT, interlude.renderMode());
        assertTrue(Math.abs(interlude.rollDegrees()) <= 1.10F);

        var finalState = NinthFormRenderState.resolve(
                NinthFormPhase.FINAL, "ninefold_gaze", 47, 0, 19.0F);
        assertTrue(finalState.keelExposed());
        assertTrue(finalState.emissiveAlpha() <= NinthFormRenderState.MAX_EMISSIVE_ALPHA);

        var banished = NinthFormRenderState.resolve(
                NinthFormPhase.BANISHED, "ninefold_gaze", 48, 0, 19.0F);
        assertEquals(0.0F, banished.emissiveAlpha());
    }

    @Test
    void emissiveEnvelopeStaysRestrainedAcrossEverySyncedState() {
        for (NinthFormPhase phase : NinthFormPhase.values()) {
            for (AttackTiming attack : AttackTiming.values()) {
                for (int tick = 0; tick <= Math.max(1, attack.totalTicks() + 1); tick++) {
                    for (float age : new float[] {0.0F, 17.0F, 89.5F}) {
                        float alpha = NinthFormRenderState.resolve(
                                        phase, attack.id(), tick, 0, age)
                                .emissiveAlpha();
                        assertTrue(alpha >= 0.0F);
                        assertTrue(alpha <= NinthFormRenderState.MAX_EMISSIVE_ALPHA);
                    }
                }
            }
        }
    }

    private static void assertTiming(
            AttackTiming attack, int windup, int active, int recovery) {
        assertEquals(windup, attack.windupTicks());
        assertEquals(active, attack.activeTicks());
        assertEquals(recovery, attack.recoveryTicks());
        assertEquals(AttackWindow.WINDUP, attack.windowAt(0));
        assertEquals(AttackWindow.WINDUP, attack.windowAt(windup - 1));
        assertEquals(AttackWindow.ACTIVE, attack.windowAt(windup));
        assertEquals(AttackWindow.RECOVERY, attack.windowAt(windup + active));
        assertEquals(AttackWindow.COMPLETE, attack.windowAt(windup + active + recovery));
    }
}
