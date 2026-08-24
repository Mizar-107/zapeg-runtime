package io.github.mizar107.zapegruntime.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoundedPlayerStateTest {

    @Test
    void capacityIsHardAndExistingUuidMayBeReplaced() {
        BoundedPlayerState<String> state = new BoundedPlayerState<>(2);
        UUID first = uuid(1);
        UUID second = uuid(2);
        UUID third = uuid(3);

        assertTrue(state.put(first, "a"));
        assertTrue(state.put(second, "b"));
        assertFalse(state.put(third, "c"));
        assertTrue(state.put(first, "updated"));
        assertEquals(2, state.size());
        assertEquals("updated", state.get(first));
        assertNull(state.get(third));
    }

    @Test
    void logoutDimensionAndServerStyleCleanupAreExplicitAndComplete() {
        BoundedPlayerState<String> state = new BoundedPlayerState<>(3);
        UUID first = uuid(1);
        UUID second = uuid(2);
        state.put(first, "a");
        state.put(second, "b");

        state.remove(first);
        assertNull(state.get(first));
        state.retainOnly(Set.of());
        assertEquals(0, state.size());
        state.put(second, "again");
        state.clear();
        assertEquals(0, state.size());
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
