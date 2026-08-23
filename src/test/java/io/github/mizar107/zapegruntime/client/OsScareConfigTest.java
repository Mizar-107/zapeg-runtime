package io.github.mizar107.zapegruntime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.client.os.OsScareToggles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OsScareConfigTest {

    private static final Path CLIENT_SOURCE = Path.of(
            "src", "main", "java", "io", "github", "mizar107",
            "zapegruntime", "client");

    @Test
    void unloadedConfigFailsClosedAtRuntime() {
        assertFalse(OsScareConfig.SPEC.isLoaded());
        assertEquals(OsScareToggles.ALL_OFF, OsScareConfig.toggles());
    }

    @Test
    void sourceContractPinsVersionedConsentAndBothFailClosedReturns() throws IOException {
        String config = Files.readString(CLIENT_SOURCE.resolve("OsScareConfig.java"));

        assertTrue(config.contains(".define(\"externalEffectsOptInV2\", false)"));
        assertTrue(config.contains(".define(\"enabled\", false)"));
        assertFalse(config.contains(".define(\"enabled\", true)"));
        assertTrue(config.contains("EXTERNAL_EFFECTS_OPT_IN_V2.get()"));
        assertTrue(config.contains("LEGACY_ENABLED.get()"));
        assertTrue(config.contains("ExternalEffectsConsent.resolve("));
        assertEquals(2, occurrences(config, "return OsScareToggles.ALL_OFF;"),
                "unloaded and exceptional reads must both fail closed");
    }

    @Test
    void startupDiagnosticCannotDescribeDefaultClientAsOptedIn() throws IOException {
        String events = Files.readString(CLIENT_SOURCE.resolve("ClientModEvents.java"));

        assertTrue(events.contains("if (!toggles.master())"));
        assertTrue(events.contains(
                "OS scare V2 opt-in disabled; legacy enabled is ignored"));
        assertTrue(events.contains("OS scare V2 opt-in enabled; effect preflight {}"));
        assertFalse(events.contains("OS effect preflight {}"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }
}
