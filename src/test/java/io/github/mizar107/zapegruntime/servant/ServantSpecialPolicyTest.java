package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServantSpecialPolicyTest {

    @Test
    void onlyTheOwnedVisibleLoadedTargetInsideRangeCanBeHit() {
        ServantArchetype archetype = ServantArchetype.BINDER;
        assertEquals(ServantSpecialPolicy.Result.HIT, evaluate(archetype, facts(
                true, true, true, true, true, archetype.specialRange() * archetype.specialRange())));
        assertEquals(ServantSpecialPolicy.Result.WRONG_TARGET, evaluate(archetype, facts(
                false, true, true, true, true, 1.0D)));
        assertEquals(ServantSpecialPolicy.Result.TARGET_UNAVAILABLE, evaluate(archetype, facts(
                true, false, true, true, true, 1.0D)));
        assertEquals(ServantSpecialPolicy.Result.WRONG_DIMENSION, evaluate(archetype, facts(
                true, true, false, true, true, 1.0D)));
        assertEquals(ServantSpecialPolicy.Result.UNLOADED, evaluate(archetype, facts(
                true, true, true, false, true, 1.0D)));
        assertEquals(ServantSpecialPolicy.Result.OBSCURED, evaluate(archetype, facts(
                true, true, true, true, false, 1.0D)));
        assertEquals(ServantSpecialPolicy.Result.OUT_OF_RANGE, evaluate(archetype, facts(
                true, true, true, true, true, archetype.specialRange() * archetype.specialRange()
                        + 0.001D)));
        assertEquals(ServantSpecialPolicy.Result.OUT_OF_RANGE, evaluate(archetype, facts(
                true, true, true, true, true, Double.NaN)));
    }

    private static ServantSpecialPolicy.Result evaluate(
            ServantArchetype archetype,
            ServantSpecialPolicy.TargetFacts facts) {
        return ServantSpecialPolicy.evaluate(archetype, facts);
    }

    private static ServantSpecialPolicy.TargetFacts facts(
            boolean owned,
            boolean alive,
            boolean sameDimension,
            boolean loaded,
            boolean lineOfSight,
            double distanceSquared) {
        return new ServantSpecialPolicy.TargetFacts(
                owned,
                alive,
                sameDimension,
                loaded,
                lineOfSight,
                distanceSquared);
    }
}
