package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Encounter-id/phase/cycle selection with no mutable RNG and no immediate repeat. */
public final class NinthFormAttackSelector {

    private NinthFormAttackSelector() {}

    public static NinthFormAttack select(
            UUID encounterId,
            NinthFormPhase phase,
            long attackCycle,
            String previousAttackId) {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(phase, "phase");
        if (attackCycle < 0L) {
            throw new IllegalArgumentException("attackCycle cannot be negative");
        }
        List<NinthFormAttack> candidates = Arrays.stream(NinthFormAttack.values())
                .filter(attack -> attack.allowedIn(phase))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("phase has no Ninth Form attacks");
        }
        long mixed = mix64(encounterId.getMostSignificantBits()
                ^ Long.rotateLeft(encounterId.getLeastSignificantBits(), 19)
                ^ Long.rotateLeft(attackCycle, 7)
                ^ ((long) phase.sequence() * 0x9e3779b97f4a7c15L));
        int index = (int) Math.floorMod(mixed, candidates.size());
        NinthFormAttack selected = candidates.get(index);
        if (candidates.size() > 1 && selected.serializedName().equals(previousAttackId)) {
            selected = candidates.get((index + 1) % candidates.size());
        }
        return selected;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
