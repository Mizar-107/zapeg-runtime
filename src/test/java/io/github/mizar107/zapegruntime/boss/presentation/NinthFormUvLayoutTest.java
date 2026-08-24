package io.github.mizar107.zapegruntime.boss.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.combat.NinthFormPartKind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NinthFormUvLayoutTest {

    @Test
    void everyCuboidUnwrapIsExactBoundedAndDisjoint() {
        assertEquals(512, NinthFormUvLayout.WIDTH);
        assertEquals(512, NinthFormUvLayout.HEIGHT);
        assertEquals(10, NinthFormUvLayout.BOXES.size());
        int usedPixels = 0;
        for (int index = 0; index < NinthFormUvLayout.BOXES.size(); index++) {
            var box = NinthFormUvLayout.BOXES.get(index);
            assertEquals(2 * (box.sizeX() + box.sizeZ()), box.pixelWidth());
            assertEquals(box.sizeY() + box.sizeZ(), box.pixelHeight());
            assertTrue(box.insideAtlas(), box.name());
            usedPixels += box.pixelWidth() * box.pixelHeight();
            for (int other = index + 1; other < NinthFormUvLayout.BOXES.size(); other++) {
                assertFalse(box.overlaps(NinthFormUvLayout.BOXES.get(other)), box.name());
            }
        }
        assertEquals(236_800, usedPixels);
    }

    @Test
    void allFiveNativeVisualCubesFitInsideTheirServerHitParts() {
        Map<NinthFormPartKind, NinthFormUvLayout.UvBox> visuals = Map.of(
                NinthFormPartKind.PROW_LANTERN, NinthFormUvLayout.PROW_LANTERN,
                NinthFormPartKind.PORT_MOORING, NinthFormUvLayout.PORT_MOORING,
                NinthFormPartKind.STARBOARD_MOORING, NinthFormUvLayout.STARBOARD_MOORING,
                NinthFormPartKind.KEEL_HEART, NinthFormUvLayout.KEEL_HEART,
                NinthFormPartKind.ARMORED_HULL_AFT, NinthFormUvLayout.ARMORED_HULL_AFT);
        assertEquals(NinthFormPartKind.values().length, visuals.size());
        for (NinthFormPartKind kind : NinthFormPartKind.values()) {
            var visual = visuals.get(kind);
            assertTrue(visual.sizeX() / 16.0F <= kind.width(), kind.serializedName());
            assertTrue(visual.sizeY() / 16.0F <= kind.height(), kind.serializedName());
        }
    }
}
