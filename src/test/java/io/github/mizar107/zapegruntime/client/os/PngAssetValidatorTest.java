package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class PngAssetValidatorTest {

    @Test
    void bundledPopupAssetPassesTheAwtFreePreflight() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(PlatformOsScare.FACE_RESOURCE)) {
            PngAssetValidator.Validation result = PngAssetValidator.validate(stream);
            assertTrue(result.valid());
            assertEquals(OsEffectReason.NONE, result.reason());
            assertEquals(280, result.width());
            assertEquals(360, result.height());
        }
    }

    @Test
    void missingAndMalformedAssetsReturnBoundedReasons() {
        PngAssetValidator.Validation missing = PngAssetValidator.validate(null);
        assertFalse(missing.valid());
        assertEquals(OsEffectReason.ASSET_MISSING, missing.reason());

        PngAssetValidator.Validation malformed = PngAssetValidator.validate(
                new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        assertFalse(malformed.valid());
        assertEquals(OsEffectReason.ASSET_INVALID, malformed.reason());
    }
}
