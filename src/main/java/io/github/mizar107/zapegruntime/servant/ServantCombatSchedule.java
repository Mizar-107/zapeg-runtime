package io.github.mizar107.zapegruntime.servant;

import java.util.Objects;
import java.util.UUID;

/** Pure, restart-stable scheduling for bounded Servant special attacks. */
public final class ServantCombatSchedule {

    public static final int MIN_INITIAL_DELAY_TICKS = 36;
    public static final int INITIAL_DELAY_JITTER_TICKS = 20;
    public static final int MAX_SEQUENCE = 1_000_000;

    private ServantCombatSchedule() {}

    public static int initialDelay(UUID encounterId, ServantArchetype archetype) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(archetype, "archetype");
        return MIN_INITIAL_DELAY_TICKS + bounded(
                encounterId,
                archetype,
                0,
                INITIAL_DELAY_JITTER_TICKS + 1);
    }

    public static int cooldown(
            UUID encounterId,
            ServantArchetype archetype,
            int completedSequence) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(archetype, "archetype");
        int boundedSequence = Math.max(0, Math.min(completedSequence, MAX_SEQUENCE));
        return archetype.cooldownTicks() + bounded(
                encounterId,
                archetype,
                boundedSequence + 1,
                archetype.cooldownJitterTicks() + 1);
    }

    public static long addWithoutOverflow(long now, int delay) {
        if (now < 0L || delay < 0) {
            throw new IllegalArgumentException("time and delay must be non-negative");
        }
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }

    private static int bounded(
            UUID encounterId,
            ServantArchetype archetype,
            int sequence,
            int bound) {
        long input = encounterId.getMostSignificantBits()
                ^ Long.rotateLeft(encounterId.getLeastSignificantBits(), 23)
                ^ ((long) archetype.ordinal() * 0x9E3779B97F4A7C15L)
                ^ ((long) sequence * 0xD1B54A32D192ED03L);
        long mixed = mix64(input);
        return (int) Long.remainderUnsigned(mixed, bound);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
