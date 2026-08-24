package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class NinthFormPartGeometryTest {

    @Test
    void fiveNamedPartsExposeExactlyThreeDistinctWeakPointBits() {
        assertEquals(5, NinthFormPartKind.values().length);
        Set<Integer> bits = Arrays.stream(NinthFormPartKind.values())
                .filter(NinthFormPartKind::weakPoint)
                .map(NinthFormPartKind::weakPointBit)
                .collect(Collectors.toSet());
        assertEquals(Set.of(0b001, 0b010, 0b100), bits);
        assertEquals(3, bits.size());
        Arrays.stream(NinthFormPartKind.values()).forEach(kind -> {
            assertTrue(kind.width() > 0.0F);
            assertTrue(kind.height() > 0.0F);
            assertTrue(Double.isFinite(kind.lateralOffset()));
            assertTrue(Double.isFinite(kind.forwardOffset()));
            assertTrue(Double.isFinite(kind.verticalOffset()));
        });
    }

    @Test
    void loadedFootprintWindowIsBoundedAndRejectsInvalidGeometry() {
        NinthFormLoadedFootprint.ChunkWindow window =
                NinthFormLoadedFootprint.chunkWindow(new AABB(-12, 60, -12, 12, 71, 12));
        assertNotNull(window);
        assertEquals(4L, window.count());
        assertTrue(window.count() <= NinthFormLoadedFootprint.MAX_FOOTPRINT_CHUNKS);
        assertNull(NinthFormLoadedFootprint.chunkWindow(
                new AABB(Double.NaN, 0, 0, 1, 1, 1)));
    }
}
