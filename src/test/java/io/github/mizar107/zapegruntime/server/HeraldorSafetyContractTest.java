package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HeraldorSafetyContractTest {

    @Test
    void commandTreeExposesOnlyTheAgreedOperatorSurface() throws IOException {
        String source = source("server/HeraldorSafetyCommands.java");
        assertTrue(source.contains("Commands.literal(\"status\")"));
        assertTrue(source.contains("Commands.literal(\"stop\")"));
        assertTrue(source.contains("Commands.literal(\"cleanup\")"));
        assertTrue(source.contains("Commands.literal(\"arm\")"));
        assertTrue(source.contains("armMode(\"manual\""));
        assertTrue(source.contains("armMode(\"live\""));
        assertTrue(source.contains("armMode(\"auto\""));
        assertFalse(source.contains("Commands.literal(\"set\")"));
        assertFalse(source.contains("Commands.literal(\"quarantine\")"));
    }

    @Test
    void stopFlushesQuarantineBeforeTheFirstCleanupMutation() throws IOException {
        String source = source("server/HeraldorSafetyController.java");
        int stop = source.indexOf("public static StopOutcome emergencyStop");
        int flush = source.indexOf("flushSafetyAuthority(server)", stop);
        int cleanup = source.indexOf("cleanup(server, persistenceFailures)", stop);
        assertTrue(stop >= 0 && flush > stop && cleanup > flush);
        assertTrue(source.contains("server.overworld().getDataStorage().save()"));
    }

    @Test
    void durableAndAutonomousMutationSeamsAreGated() throws IOException {
        assertTrue(source("story/StoryService.java").contains("HeraldorSafetyMode.AUTO"));
        assertTrue(source("director/HeraldorDirector.java").contains("HeraldorSafetyMode.AUTO"));
        String scenes = source("server/SceneServerManager.java");
        assertTrue(scenes.contains("HeraldorSafetyMode.MANUAL"));
        assertTrue(scenes.contains("HeraldorSafetyMode.LIVE"));
        assertTrue(scenes.contains("HeraldorSafetyMode.AUTO"));
        assertTrue(source("server/SceneServerEvents.java").contains("HeraldorSafetyController.enforce"));
        assertTrue(source("server/SceneServerEvents.java").contains("ServerStartedEvent"));
        assertTrue(source("server/SceneServerEvents.java").contains("HeraldorSafetyController.forget"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/io/github/mizar107/zapegruntime/" + relative));
    }
}
