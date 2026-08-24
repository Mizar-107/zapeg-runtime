package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DirectorArchitectureContractTest {

    private static final Path MAIN = Path.of("src", "main", "java", "io", "github",
            "mizar107", "zapegruntime");

    @Test
    void onlyDirectorDispatchCarriesStoryProvenance() throws IOException {
        String manager = source("server", "SceneServerManager.java");
        assertTrue(manager.contains("dispatchDirector("));
        assertTrue(manager.contains("DirectorSceneIdentity directorIdentity"));
        assertTrue(manager.contains("replayIdentity,\n                null"),
                "timeline dispatch must have no Director identity");
        assertTrue(manager.contains("null,\n                null);"),
                "manual/rehearsal dispatch must terminate with null provenance");
        assertTrue(manager.contains("current.directorIdentity != null"));
        assertTrue(manager.indexOf("acceptsAcknowledgement(message.acknowledgement())")
                        < manager.indexOf("HeraldorDirector.onAcknowledgement("),
                "proof callback must be below active profile acknowledgement validation");
        assertTrue(manager.indexOf("osScareStatuses.recordStatus(")
                        < manager.indexOf("HeraldorDirector.onOsScareStatus("),
                "fallback proof must follow accepted ledger commit");
    }

    @Test
    void timersAndServerCancellationCannotMasqueradeAsCompletion() throws IOException {
        String manager = source("server", "SceneServerManager.java");
        String policy = source("director", "DirectorPresentationPolicy.java");
        assertTrue(manager.contains("CancelReason.EXPIRED"));
        assertTrue(manager.contains("HeraldorDirector.onCancelled(server"));
        assertFalse(policy.contains("CancelReason"));
        assertTrue(policy.contains("case GAZE -> Proof.GAZE"));
        assertTrue(policy.contains("case TIMEOUT -> Proof.TIMEOUT"));
        assertTrue(policy.contains("acknowledgement != SceneAck.VISIBLE"));
        assertTrue(policy.contains("case VISITATION_01, RIFT_01 -> false"));
    }

    @Test
    void colossusVisibleProofFollowsACompletedFrustumAcceptedRender() throws IOException {
        String manager = source("client", "ClientSceneManager.java");
        String events = source("client", "ClientSceneEvents.java");
        String renderer = source("client", "ColossusRenderer.java");
        String tick = manager.substring(
                manager.indexOf("private static void tickColossus("),
                manager.indexOf("private static void tickFalsePassage("));
        String observation = manager.substring(
                manager.indexOf("private static RenderSnapshot observeColossus("),
                manager.indexOf("static void acknowledgeColossusRendered("));

        assertFalse(tick.contains("markVisible("),
                "an audio/body tick is not presentation evidence");
        assertFalse(observation.contains("markVisible("),
                "frustum acceptance must still be followed by an actual draw");
        assertTrue(observation.contains("event.getFrustum().isVisible(bounds)"));
        int render = events.indexOf("ColossusRenderer.render(snapshot, event)");
        int acknowledge = events.indexOf("ClientSceneManager.acknowledgeColossusRendered(snapshot)");
        assertTrue(render >= 0 && acknowledge > render,
                "VISIBLE must be acknowledged after the renderer succeeds");
        assertTrue(renderer.contains("public static boolean render("));
        int completedDraw = renderer.indexOf("pose.popPose();");
        int rendered = renderer.indexOf("return true;", completedDraw);
        assertTrue(completedDraw >= 0 && rendered > completedDraw);
        assertTrue(manager.contains("static void acknowledgeColossusRendered("));
    }

    @Test
    void directorIsUuidOnlyOnlineOnlyAndDoesNotLoadChunksOrDispatchCommands()
            throws IOException {
        String director = source("director", "HeraldorDirector.java");
        String commands = source("director", "DirectorCommands.java");
        String reconciler = source("director", "ServantBarrierReconciler.java");
        String all = director + commands + reconciler;
        assertTrue(director.contains("getPlayerList().getPlayers()"));
        assertTrue(commands.contains("UuidArgument.uuid()"));
        assertFalse(commands.contains("EntityArgument"));
        assertFalse(all.contains("getGameProfile().getName()"));
        assertFalse(all.contains("getChunk("));
        assertFalse(all.contains("setChunkForced"));
        assertFalse(all.contains("performCommand"));
        assertFalse(all.contains("dispatchCommand"));
    }

    @Test
    void postTransitionHookQueuesInsteadOfReenteringStoryService() throws IOException {
        String service = source("story", "StoryService.java");
        String events = source("director", "DirectorServerEvents.java");
        assertTrue(service.contains("MinecraftForge.EVENT_BUS.post(new StoryAdvancedEvent("));
        assertTrue(events.contains("HeraldorDirector.queueReconciliation("));
        assertFalse(events.contains("StoryService.submit"));
        assertTrue(events.contains("never submit another fact"));
    }

    @Test
    void startupAndPeriodicServantFallbacksHaveDifferentBoundedStrategies()
            throws IOException {
        String director = source("director", "HeraldorDirector.java");
        String reconciler = source("director", "ServantBarrierReconciler.java");
        assertTrue(director.contains("ScanMode.FULL"));
        assertTrue(director.contains("ScanMode.CURSOR"));
        assertTrue(reconciler.contains("PERIODIC_SCAN_BUDGET = 64"));
        assertTrue(reconciler.contains("MAX_CHAINED_ADVANCES = 4"));
        assertTrue(reconciler.contains("ReconcileStatus.SCAN_LIMIT"));
        assertTrue(director.contains("servant_reconcile="));
    }

    @Test
    void durableProofChecksReceiptAndRecoveryEnvelopeBeforeSubmission() throws IOException {
        String director = source("director", "HeraldorDirector.java");
        int proofMethod = director.indexOf("private static void processProof(");
        int receipt = director.indexOf("StoryWorldData.ReceiptStatus receipt", proofMethod);
        int bindingMismatch = director.indexOf("binding_definition_mismatch", proofMethod);
        int envelope = director.indexOf("boolean exactEnvelope", proofMethod);
        int submit = director.indexOf("StoryService.submitIfExpected(", proofMethod);
        assertTrue(proofMethod >= 0 && receipt > proofMethod);
        assertTrue(bindingMismatch > receipt);
        assertTrue(envelope > bindingMismatch);
        assertTrue(submit > envelope,
                "an old proven scene must not cross a story recovery epoch");
        assertTrue(director.contains("story_receipt_recovered"));
        assertTrue(director.contains("proof_superseded"));
    }

    private static String source(String directory, String filename) throws IOException {
        return Files.readString(MAIN.resolve(directory).resolve(filename));
    }
}
