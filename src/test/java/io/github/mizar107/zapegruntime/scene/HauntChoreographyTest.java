package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HauntChoreographyTest {

    @Test
    void closingCircleIsNearerThanTheDistantCircle() {
        assertTrue(
                HauntChoreography.startDistance(HauntChoreography.STAGE_CLOSING)
                        < HauntChoreography.startDistance(HauntChoreography.STAGE_CIRCLE));
        assertTrue(
                HauntChoreography.endDistance(HauntChoreography.STAGE_CLOSING)
                        < HauntChoreography.endDistance(HauntChoreography.STAGE_CIRCLE));
        assertTrue(
                HauntChoreography.endDistance(HauntChoreography.STAGE_CIRCLE) > 2.5D);
        assertTrue(HauntChoreography.isCircle(0));
        assertTrue(HauntChoreography.isCircle(1));
        assertTrue(HauntChoreography.isWhisper(2));
        assertFalse(HauntChoreography.isCircle(2));
        assertEquals(0, HauntChoreography.stepCount(HauntChoreography.STAGE_WHISPER));
    }
}
