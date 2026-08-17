package io.github.mizar107.zapegruntime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            assertTrue(metadata.contains("displayTest = \"MATCH_VERSION\""));
            assertTrue(metadata.contains("side = \"BOTH\""));
            assertTrue(!metadata.toLowerCase().contains("heraldor"));
        }
    }
}
