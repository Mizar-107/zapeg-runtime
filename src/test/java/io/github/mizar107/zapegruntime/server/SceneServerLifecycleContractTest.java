package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SceneServerLifecycleContractTest {

    private static final Path SERVER_SOURCE = Path.of("src", "main", "java", "io", "github",
            "mizar107", "zapegruntime", "server");

    @Test
    void stoppingServerClearsActiveSceneAndRetainedOsDiagnostics() throws IOException {
        String events = Files.readString(SERVER_SOURCE.resolve("SceneServerEvents.java"));
        String manager = Files.readString(SERVER_SOURCE.resolve("SceneServerManager.java"));

        assertTrue(events.contains("onServerStopping(ServerStoppingEvent event)"));
        assertTrue(events.contains("SceneServerManager.shutdown()"));
        assertTrue(manager.contains("public static void shutdown()"));
        assertTrue(manager.contains("cancel(CancelReason.SERVER_STOP)"));
        assertTrue(manager.contains("osScareStatuses.clear()"));
    }
}
