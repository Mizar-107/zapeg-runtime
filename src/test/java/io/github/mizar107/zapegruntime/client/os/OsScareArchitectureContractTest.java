package io.github.mizar107.zapegruntime.client.os;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OsScareArchitectureContractTest {

    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "io", "github", "mizar107", "zapegruntime");
    private static final Path CLIENT_OS_ROOT = JAVA_ROOT.resolve(Path.of("client", "os"));

    @Test
    void awtBoundaryCannotBeLinkedFromCommonOrDedicatedServerCode() throws IOException {
        try (var sources = Files.walk(JAVA_ROOT)) {
            for (Path source : sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(CLIENT_OS_ROOT))
                    .toList()) {
                String text = Files.readString(source);
                assertFalse(text.contains("PlatformOsScare"), source.toString());
                assertFalse(text.contains("java.awt."), source.toString());
                assertFalse(text.contains("javax.swing."), source.toString());
            }
        }

        String clientEvents = Files.readString(
                JAVA_ROOT.resolve(Path.of("client", "ClientModEvents.java")));
        assertTrue(clientEvents.contains("value = Dist.CLIENT"));
    }

    @Test
    void popupBoundaryHasNoExternalPersistenceOrSystemIntegration() throws IOException {
        String source = Files.readString(CLIENT_OS_ROOT.resolve("PlatformOsScare.java"));

        assertFalse(source.contains("ProcessBuilder"));
        assertFalse(source.contains("Runtime.getRuntime"));
        assertFalse(source.contains("java.net."));
        assertFalse(source.contains("java.nio.file"));
        assertFalse(source.contains("getSystemClipboard"));
        assertFalse(source.contains("Desktop.getDesktop"));
        assertTrue(source.contains("getResourceAsStream"),
                "the face remains a classpath asset, never a filesystem payload");
    }

    @Test
    void ownershipAndPlacementProofsRemainExplicitInProductionBoundary()
            throws IOException {
        String source = Files.readString(CLIENT_OS_ROOT.resolve("PlatformOsScare.java"));

        assertTrue(source.contains("PopupOwnership"));
        assertTrue(source.contains("placementMatches(window, target)"));
        assertTrue(source.contains("session.window.setVisible(false)"));
        assertTrue(source.contains("session.window.isDisplayable()"));
        assertTrue(source.contains("POPUP_OWNERSHIP.release(session.token)"));
    }
}
