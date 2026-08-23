package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ServantArchetypeTest {

    @Test
    void closedSetContainsExactlyTheThreeDesignedArchetypes() {
        assertEquals(3, ServantArchetype.values().length);
        assertEquals(
                Set.of("stalker", "herald", "binder"),
                Arrays.stream(ServantArchetype.values())
                        .map(ServantArchetype::id)
                        .collect(Collectors.toSet()));
        assertEquals(ServantArchetype.STALKER, ServantArchetype.fromId("STALKER").orElseThrow());
        assertFalse(ServantArchetype.fromId("servant").isPresent());
        assertFalse(ServantArchetype.fromId("s".repeat(17)).isPresent());
        assertFalse(ServantArchetype.fromId(null).isPresent());
    }

    @Test
    void everyCombatProfileIsReadableAndHardBounded() {
        for (ServantArchetype archetype : ServantArchetype.values()) {
            assertTrue(archetype.maxHealth() >= 40.0D && archetype.maxHealth() <= 64.0D);
            assertTrue(archetype.armor() >= 0.0D && archetype.armor() <= 12.0D);
            assertTrue(archetype.attackDamage() >= 3.0D && archetype.attackDamage() <= 8.0D);
            assertTrue(archetype.movementSpeed() >= 0.2D && archetype.movementSpeed() <= 0.35D);
            assertTrue(archetype.knockbackResistance() >= 0.0D
                    && archetype.knockbackResistance() <= 0.7D);
            assertTrue(archetype.specialRange() >= 4.0D && archetype.specialRange() <= 12.0D);
            assertTrue(archetype.specialDamage() > 0.0F && archetype.specialDamage() <= 5.0F);
            assertTrue(archetype.telegraphTicks() >= 14 && archetype.telegraphTicks() <= 30);
            assertTrue(archetype.cooldownTicks() >= 70 && archetype.cooldownTicks() <= 110);
            assertTrue(archetype.cooldownJitterTicks() <= 20);
            assertTrue(archetype.spawnDistance() >= 4.0D && archetype.spawnDistance() <= 6.0D);
        }
    }
}
