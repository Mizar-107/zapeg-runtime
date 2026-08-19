package io.github.mizar107.zapegruntime.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScenePaletteTest {

    @Test
    void theSignatureEyeColorIsEmberOrange() {
        // Orange, not red and not yellow: red dominates, green is a clear
        // minority but present, blue is nearly absent.
        assertTrue(ScenePalette.EYE_RED > ScenePalette.EYE_GREEN);
        assertTrue(ScenePalette.EYE_GREEN > ScenePalette.EYE_BLUE);
        assertTrue(ScenePalette.EYE_GREEN >= 0.3F && ScenePalette.EYE_GREEN <= 0.6F,
                "too little green reads red, too much reads yellow");
        assertTrue(ScenePalette.EYE_BLUE <= 0.15F);
    }

    @Test
    void theHaloIsSofterAndWiderThanTheCore() {
        assertTrue(ScenePalette.EYE_HALO_ALPHA_SCALE > 0.0F
                && ScenePalette.EYE_HALO_ALPHA_SCALE < 1.0F);
        assertTrue(ScenePalette.EYE_HALO_SIZE_SCALE > 1.0F
                && ScenePalette.EYE_HALO_SIZE_SCALE <= 3.0F);
    }

    @Test
    void eyesHoldAtFullStrengthThenFadeLast() {
        // While the body is still mostly present the eyes do not dim at all.
        assertEquals(1.0D, ScenePalette.eyeHold(1.0D));
        assertEquals(1.0D, ScenePalette.eyeHold(ScenePalette.EYE_HOLD_ENVELOPE));
        assertEquals(1.0D, ScenePalette.eyeHold(0.8D));
        // Below the hold they fade linearly to nothing.
        assertEquals(0.5D, ScenePalette.eyeHold(ScenePalette.EYE_HOLD_ENVELOPE * 0.5D), 1.0E-9D);
        assertEquals(0.0D, ScenePalette.eyeHold(0.0D));
        double previous = -1.0D;
        for (double envelope = 0.0D; envelope <= 1.0D; envelope += 0.02D) {
            double hold = ScenePalette.eyeHold(envelope);
            assertTrue(hold >= previous, "eye hold must be monotonic in the envelope");
            assertTrue(hold >= 0.0D && hold <= 1.0D);
            previous = hold;
        }
        assertEquals(0.0D, ScenePalette.eyeHold(Double.NaN));
    }

    @Test
    void eyesNeverShineThroughTheBackOfTheHead() {
        assertEquals(1.0D, ScenePalette.frontality(1.0D));
        assertEquals(0.0D, ScenePalette.frontality(-1.0D));
        assertEquals(0.0D, ScenePalette.frontality(-0.02D));
        double previous = -1.0D;
        for (double cosine = -1.0D; cosine <= 1.0D; cosine += 0.02D) {
            double fade = ScenePalette.frontality(cosine);
            assertTrue(fade >= previous, "frontality must be monotonic in the cosine");
            assertTrue(fade >= 0.0D && fade <= 1.0D);
            previous = fade;
        }
    }
}
