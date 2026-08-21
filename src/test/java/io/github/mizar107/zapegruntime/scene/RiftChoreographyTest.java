package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RiftChoreographyTest {

    @Test
    void stagesStayInsideThePhotosensitivityBudget() {
        for (int stage = 0; stage <= RiftChoreography.MAX_STAGE; stage++) {
            assertTrue(
                    RiftChoreography.pulseTicks(stage) >= RiftChoreography.MIN_PULSE_TICKS,
                    "pulse must stay under 3 flashes per second");
            assertTrue(RiftChoreography.washScale(stage) > 0.0D);
            assertTrue(RiftChoreography.washScale(stage) <= 1.0D);
        }
        // The photosensitivity budget is the slow pulse, not the depth: the
        // wash may go near-black but never fully opaque, and never fast.
        assertTrue(RiftChoreography.MAX_WASH_ALPHA <= 240);
        assertTrue(RiftChoreography.ECLIPSE_FOG_FAR_SCALE >= 0.35F);
        assertTrue(RiftChoreography.ECLIPSE_FOG_FAR_SCALE <= 0.55F);
    }

    @Test
    void theEclipseActuallyReadsNearBlackAtPeakOnItsSlowestEase() {
        // The manifestation opener: peak wash (cap x eclipse scale) must land
        // in the near-black band, and the eclipse must breathe on the
        // slowest pulse of the whole family — a swelling hold, not a
        // flicker.
        int peak = (int) Math.round(
                RiftChoreography.MAX_WASH_ALPHA
                        * RiftChoreography.washScale(RiftChoreography.STAGE_ECLIPSE));
        assertTrue(peak >= 210, "daytime eclipse must read near-black, not a tint");
        assertTrue(peak < 255, "some world must always survive under the wash");
        for (int stage = 0; stage <= RiftChoreography.MAX_STAGE; stage++) {
            assertTrue(
                    RiftChoreography.pulseTicks(RiftChoreography.STAGE_ECLIPSE)
                            >= RiftChoreography.pulseTicks(stage),
                    "the eclipse ease must be the slowest of the family");
        }
    }

    @Test
    void eclipseIsTheOnlyFogYieldStageAndWitnessHidesTheHud() {
        assertTrue(RiftChoreography.isEclipse(0));
        assertEquals(1.0F, RiftChoreography.extraFogDip(0));
        assertFalse(RiftChoreography.hidesHud(0));
        for (int stage = 1; stage <= RiftChoreography.MAX_STAGE; stage++) {
            assertEquals(0.0F, RiftChoreography.extraFogDip(stage));
        }
        assertTrue(RiftChoreography.hidesHud(RiftChoreography.STAGE_WITNESS));
        assertTrue(RiftChoreography.isUnmoor(RiftChoreography.STAGE_UNMOOR));
        assertTrue(RiftChoreography.isTear(RiftChoreography.STAGE_TEAR));
    }

    @Test
    void hueCrawlsAndWarpStaysSmall() {
        double first = RiftChoreography.hueDegrees(0.0D, 0L);
        double later = RiftChoreography.hueDegrees(200.0D, 0L);
        assertTrue(Math.abs(later - first) > 40.0D, "hue must actually travel");
        assertTrue(RiftChoreography.hueDegrees(200.0D, 0L) < 360.0D);
        for (int row = 0; row < 400; row++) {
            int warp = RiftChoreography.warpPixels(40.0D, row, 7L);
            assertTrue(Math.abs(warp) <= 6, "warp is a smear, not a shake");
        }
        assertEquals(0.0D, RiftChoreography.hueDegrees(Double.NaN, 1L));
        assertEquals(0, RiftChoreography.warpPixels(Double.NaN, 0, 1L));
    }
}
