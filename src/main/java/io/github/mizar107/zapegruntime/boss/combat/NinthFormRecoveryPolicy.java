package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import java.util.Objects;

/** Crash/reload rule: preserve deterministic identity/cycle, replay the full windup. */
public final class NinthFormRecoveryPolicy {

    private NinthFormRecoveryPolicy() {}

    public static NinthFormCombatSnapshot.CombatState restartAtWindup(
            NinthFormCombatSnapshot.CombatState combat) {
        Objects.requireNonNull(combat, "combat");
        return new NinthFormCombatSnapshot.CombatState(
                combat.brokenPointMask(), combat.attackCycle(), combat.attackId(), 0);
    }
}
