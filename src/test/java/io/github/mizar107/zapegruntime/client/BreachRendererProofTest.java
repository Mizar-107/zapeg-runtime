package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreachRendererProofTest {

    @Test
    void fractionalOpacityCannotClaimAFrameBeforeArgbAlphaIsNonzero() {
        // This was the exact false-positive: the old floating-point gate
        // accepted veil=0.002073..., while round(154 * veil) was still zero.
        assertFalse(BreachRenderer.hasVisiblePrimitives(
                320, 180, 2.5D, 170, 42L));
        assertTrue(BreachRenderer.hasVisiblePrimitives(
                320, 180, 3.0D, 170, 42L));
    }

    @Test
    void proofRequiresNontransparentNonemptyViewportIntersection() {
        int opaque = 0x01010204;
        assertFalse(BreachRenderer.hasClippedNonTransparentArea(
                320, 180, 1, 1, 20, 20, 0x00010204));
        assertFalse(BreachRenderer.hasClippedNonTransparentArea(
                320, 180, 8, 8, 8, 20, opaque));
        assertFalse(BreachRenderer.hasClippedNonTransparentArea(
                320, 180, -20, -20, -1, -1, opaque));
        assertTrue(BreachRenderer.hasClippedNonTransparentArea(
                320, 180, -20, -20, 1, 1, opaque));
        assertTrue(BreachRenderer.hasClippedNonTransparentArea(
                320, 180, 20, 20, 1, 1, opaque), "reversed fill coordinates stay valid");
    }

    @Test
    void undersizedViewportCannotProduceRenderProof() {
        assertFalse(BreachRenderer.hasVisiblePrimitives(
                7, 180, 90.0D, 170, 42L));
        assertFalse(BreachRenderer.hasVisiblePrimitives(
                320, 7, 90.0D, 170, 42L));
    }
}
