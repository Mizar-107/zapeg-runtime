package io.github.mizar107.zapegruntime.boss.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    @Test
    void finalHeartIsAboveGroundAndAFrontalRayHitsItBeforeTheParentHull() {
        NinthFormPartKind heart = NinthFormPartKind.KEEL_HEART;
        double halfWidth = heart.width() / 2.0D;
        AABB heartBox = new AABB(
                heart.lateralOffset() - halfWidth,
                heart.verticalOffset(),
                heart.forwardOffset() - halfWidth,
                heart.lateralOffset() + halfWidth,
                heart.verticalOffset() + heart.height(),
                heart.forwardOffset() + halfWidth);
        AABB parentBox = new AABB(-4.0D, 0.0D, -4.0D, 4.0D, 6.0D, 4.0D);
        assertTrue(heartBox.minY > 0.0D, "heart cannot be buried below the arena floor");
        assertFalse(parentBox.contains(0.0D, 2.5D, heartBox.maxZ));

        Vec3 rayStart = new Vec3(0.0D, 2.5D, 12.0D);
        Vec3 rayEnd = new Vec3(0.0D, 2.5D, -12.0D);
        Vec3 heartHit = heartBox.clip(rayStart, rayEnd).orElseThrow();
        Vec3 parentHit = parentBox.clip(rayStart, rayEnd).orElseThrow();
        assertTrue(
                rayStart.distanceToSqr(heartHit) < rayStart.distanceToSqr(parentHit),
                "the exposed heart must win normal entity ray ordering");
    }
}
