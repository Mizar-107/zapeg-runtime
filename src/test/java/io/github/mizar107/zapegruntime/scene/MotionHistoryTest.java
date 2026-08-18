package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MotionHistoryTest {

    @Test
    void delayAndWraparoundReturnTheExpectedSample() {
        MotionHistory history = new MotionHistory(4, 2);
        history.record(new Vec3(0.0D, 64.0D, 0.0D), 10.0F);
        history.record(new Vec3(1.0D, 64.0D, 0.0D), 20.0F);
        assertTrue(history.delayedSample().isEmpty());

        history.record(new Vec3(2.0D, 64.0D, 0.0D), 30.0F);
        assertEquals(
                new MotionHistory.Sample(new Vec3(0.0D, 64.0D, 0.0D), 10.0F),
                history.delayedSample().orElseThrow());

        history.record(new Vec3(3.0D, 64.0D, 0.0D), 40.0F);
        history.record(new Vec3(4.0D, 64.0D, 0.0D), 50.0F);
        assertEquals(4, history.size());
        assertEquals(
                new MotionHistory.Sample(new Vec3(2.0D, 64.0D, 0.0D), 30.0F),
                history.delayedSample().orElseThrow());
    }

    @Test
    void sampleBackWalksTheTraceNewestToOldest() {
        MotionHistory history = new MotionHistory(8, 1);
        for (int index = 0; index < 6; index++) {
            history.record(new Vec3(index, 64.0D, 0.0D), index * 10.0F);
        }
        // sampleBack(0) is the newest record; larger indices walk into the
        // past, which is what the whisper replay uses to approach the present.
        assertEquals(
                new MotionHistory.Sample(new Vec3(5.0D, 64.0D, 0.0D), 50.0F),
                history.sampleBack(0).orElseThrow());
        assertEquals(
                new MotionHistory.Sample(new Vec3(0.0D, 64.0D, 0.0D), 0.0F),
                history.sampleBack(5).orElseThrow());
        assertTrue(history.sampleBack(6).isEmpty());
        assertTrue(history.sampleBack(-1).isEmpty());
    }

    @Test
    void clearDropsAllRecordedTransforms() {
        MotionHistory history = new MotionHistory(4, 1);
        history.record(Vec3.ZERO, 0.0F);
        history.record(new Vec3(1.0D, 2.0D, 3.0D), 90.0F);
        assertTrue(history.delayedSample().isPresent());

        history.clear();

        assertEquals(0, history.size());
        assertTrue(history.delayedSample().isEmpty());
    }

    @Test
    void rejectsInvalidBoundsAndSamples() {
        assertThrows(IllegalArgumentException.class, () -> new MotionHistory(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MotionHistory(4, 0));
        assertThrows(IllegalArgumentException.class, () -> new MotionHistory(4, 4));

        MotionHistory history = new MotionHistory(4, 1);
        assertThrows(IllegalArgumentException.class, () -> history.record(null, 0.0F));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.record(new Vec3(Double.NaN, 0.0D, 0.0D), 0.0F));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.record(Vec3.ZERO, Float.POSITIVE_INFINITY));
    }
}
