package io.github.mizar107.zapegruntime.servant;

import java.util.Objects;

/** Pure fail-closed gate shared by telegraph start and damaging resolution. */
public final class ServantSpecialPolicy {

    private ServantSpecialPolicy() {}

    public static Result evaluate(ServantArchetype archetype, TargetFacts facts) {
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(facts, "facts");
        if (!facts.uuidOwned()) {
            return Result.WRONG_TARGET;
        }
        if (!facts.alive()) {
            return Result.TARGET_UNAVAILABLE;
        }
        if (!facts.sameDimension()) {
            return Result.WRONG_DIMENSION;
        }
        if (!facts.loadedCorridor()) {
            return Result.UNLOADED;
        }
        if (!facts.lineOfSight()) {
            return Result.OBSCURED;
        }
        if (!Double.isFinite(facts.distanceSquared())
                || facts.distanceSquared() > archetype.specialRange() * archetype.specialRange()) {
            return Result.OUT_OF_RANGE;
        }
        return Result.HIT;
    }

    public record TargetFacts(
            boolean uuidOwned,
            boolean alive,
            boolean sameDimension,
            boolean loadedCorridor,
            boolean lineOfSight,
            double distanceSquared) {}

    public enum Result {
        HIT,
        WRONG_TARGET,
        TARGET_UNAVAILABLE,
        WRONG_DIMENSION,
        UNLOADED,
        OBSCURED,
        OUT_OF_RANGE
    }
}
