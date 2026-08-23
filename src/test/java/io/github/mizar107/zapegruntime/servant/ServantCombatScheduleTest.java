package io.github.mizar107.zapegruntime.servant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServantCombatScheduleTest {

    private static final UUID ENCOUNTER =
            UUID.fromString("21fa4cee-28ea-4afe-b3d7-34fd7c4b4e1d");

    @Test
    void scheduleIsStableAcrossCallsAndBoundedForEveryArchetype() {
        for (ServantArchetype archetype : ServantArchetype.values()) {
            int initial = ServantCombatSchedule.initialDelay(ENCOUNTER, archetype);
            assertEquals(initial, ServantCombatSchedule.initialDelay(ENCOUNTER, archetype));
            assertTrue(initial >= ServantCombatSchedule.MIN_INITIAL_DELAY_TICKS);
            assertTrue(initial <= ServantCombatSchedule.MIN_INITIAL_DELAY_TICKS
                    + ServantCombatSchedule.INITIAL_DELAY_JITTER_TICKS);

            for (int sequence = 0; sequence < 100; sequence++) {
                int cooldown = ServantCombatSchedule.cooldown(ENCOUNTER, archetype, sequence);
                assertEquals(
                        cooldown,
                        ServantCombatSchedule.cooldown(ENCOUNTER, archetype, sequence));
                assertTrue(cooldown >= archetype.cooldownTicks());
                assertTrue(cooldown <= archetype.cooldownTicks()
                        + archetype.cooldownJitterTicks());
            }
        }
    }

    @Test
    void timeAdditionSaturatesAndRejectsInvalidInputs() {
        assertEquals(120L, ServantCombatSchedule.addWithoutOverflow(100L, 20));
        assertEquals(Long.MAX_VALUE, ServantCombatSchedule.addWithoutOverflow(
                Long.MAX_VALUE - 2L, 20));
        assertThrows(IllegalArgumentException.class, () ->
                ServantCombatSchedule.addWithoutOverflow(-1L, 20));
        assertThrows(IllegalArgumentException.class, () ->
                ServantCombatSchedule.addWithoutOverflow(1L, -1));
    }
}
