package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Closed six-attack vocabulary with explicit telegraph, active, and recovery windows. */
public enum NinthFormAttack {
    KEEL_SWEEP(30, 10, 26, false),
    ANCHORFALL(36, 8, 30, false),
    UNDERTOW(28, 20, 28, false),
    DROWNED_BROADSIDE(42, 8, 30, false),
    WAKE_CHARGE(32, 16, 30, true),
    NINEFOLD_GAZE(48, 18, 34, true);

    public static final int MINIMUM_WINDUP_TICKS = 24;

    private final int windupTicks;
    private final int activeTicks;
    private final int recoveryTicks;
    private final boolean phaseTwoOnly;

    NinthFormAttack(
            int windupTicks, int activeTicks, int recoveryTicks, boolean phaseTwoOnly) {
        if (windupTicks < MINIMUM_WINDUP_TICKS || activeTicks < 1 || recoveryTicks < 1) {
            throw new IllegalArgumentException("invalid Ninth Form attack timeline");
        }
        this.windupTicks = windupTicks;
        this.activeTicks = activeTicks;
        this.recoveryTicks = recoveryTicks;
        this.phaseTwoOnly = phaseTwoOnly;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int windupTicks() {
        return windupTicks;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public int recoveryTicks() {
        return recoveryTicks;
    }

    public int totalTicks() {
        return windupTicks + activeTicks + recoveryTicks;
    }

    public boolean allowedIn(NinthFormPhase phase) {
        return phase == NinthFormPhase.FINAL
                || (phase == NinthFormPhase.FIRST && !phaseTwoOnly);
    }

    public AttackWindow windowAt(int attackTick) {
        if (attackTick < 0) {
            throw new IllegalArgumentException("attackTick cannot be negative");
        }
        if (attackTick < windupTicks) {
            return AttackWindow.WINDUP;
        }
        if (attackTick < windupTicks + activeTicks) {
            return AttackWindow.ACTIVE;
        }
        if (attackTick < totalTicks()) {
            return AttackWindow.RECOVERY;
        }
        return AttackWindow.COMPLETE;
    }

    public int activeAge(int attackTick) {
        return windowAt(attackTick) == AttackWindow.ACTIVE
                ? attackTick - windupTicks
                : -1;
    }

    public static Optional<NinthFormAttack> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(attack -> attack.serializedName().equals(value))
                .findFirst();
    }

    public enum AttackWindow {
        WINDUP,
        ACTIVE,
        RECOVERY,
        COMPLETE
    }
}
