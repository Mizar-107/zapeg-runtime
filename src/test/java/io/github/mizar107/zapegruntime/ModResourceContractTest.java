package io.github.mizar107.zapegruntime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModResourceContractTest {

    @Test
    void modIsMandatoryAndNeutral() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(stream);
            String metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("modId = \"zapeg_runtime\""));
            assertTrue(metadata.contains("version = \"1.1.1\""));
            assertTrue(metadata.contains("displayName = \"Heraldor\""));
            assertTrue(metadata.contains("displayTest = \"MATCH_VERSION\""));
            assertTrue(metadata.contains("side = \"BOTH\""));
        }
    }

    @Test
    void theVisitationBinaryHidesBehindABoringName() throws IOException {
        // The visitation image (unlike the openly named original item art)
        // ships at an infrastructure-boring path with
        // no provenance note beside it: the jar must never confess.
        try (InputStream face = getClass().getResourceAsStream(
                "/assets/zapeg_runtime/textures/misc/calibration_b.png")) {
            assertNotNull(face, "the visitation image must ship at the boring path");
        }
        try (InputStream old = getClass().getResourceAsStream(
                "/assets/zapeg_runtime/textures/visitation_face.png")) {
            assertNull(old, "the old self-describing texture path must be gone");
        }
        try (InputStream provenance = getClass().getResourceAsStream(
                "/assets/zapeg_runtime/ASSETS.md")) {
            assertNull(provenance, "no provenance note may ride in the jar");
        }
    }
}
