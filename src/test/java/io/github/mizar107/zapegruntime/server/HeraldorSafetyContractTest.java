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
        int stopRevoke = source.indexOf("latchRevocation(server, data.generation())", stop);
        int barrier = source.indexOf("data.quarantineBarrierSnapshot()", stop);
        int mutation = source.indexOf("data.emergencyQuarantine()", stop);
        int cleanup = source.indexOf("cleanup(server, persistenceFailures)", stop);
        assertTrue(stop >= 0 && stopRevoke > stop && barrier > stopRevoke
                && mutation > barrier && cleanup > mutation);
        int arm = source.indexOf("public static ActionOutcome arm");
        int armRevoke = source.indexOf("latchRevocation(server, priorGeneration)", arm);
        int armClear = source.indexOf("clearEnforced(server)", armRevoke);
        int armBarrier = source.indexOf("data.quarantineBarrierSnapshot()", armClear);
        assertTrue(arm >= 0 && armRevoke > arm && armClear > armRevoke
                && armBarrier > armClear);
        assertTrue(source.contains("HeraldorSafetyPersistence.flushAndVerify(server, data)"));
        String persistence = source("server/HeraldorSafetyPersistence.java");
        assertTrue(persistence.contains("NbtIo.readCompressed"));
        assertTrue(persistence.contains("channel.force(true)"));
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
        String servant = source("servant/ServantProgressionSync.java");
        assertTrue(servant.contains("HeraldorSafetyMode.AUTO"));
        String servantManager = source("servant/ServantEncounterManager.java");
        int servantStartup = servantManager.indexOf("public static void onServerStarted");
        int startupEnforce = servantManager.indexOf(
                "HeraldorSafetyController.enforce(server)", servantStartup);
        int startupReplay = servantManager.indexOf(
                "ServantProgressionSync.replayAll(server)", servantStartup);
        assertTrue(servantStartup >= 0 && startupEnforce > servantStartup
                && startupReplay > startupEnforce);
        assertTrue(source("servant/HeraldorServant.java").contains("safetyAllowsCombat()"));
        assertTrue(source("servant/ServantLoadedEntitySweep.java").contains("getAllLevels()"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(
                "src/main/java/io/github/mizar107/zapegruntime/" + relative));
    }
}
