package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.server.HeraldorWorldData;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryService;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

/** Replays permanent Servant barriers into the sole campaign authority. */
public final class ServantProgressionSync {

    private ServantProgressionSync() {}

    /**
     * Replays only the compatibility counter after startup. Story barriers are
     * deliberately not iterated in encounter-UUID order here: the Director's
     * campaign-aware reconciler selects only the archetype expected by the
     * current node.
     */
    public static void replayAll(MinecraftServer server) {
        for (ServantEncounterData.LiveVictory barrier
                : ServantEncounterData.get(server).liveVictories()) {
            syncLegacyBarrier(server, barrier);
        }
    }

    /** Applies one barrier and emits only an advisory, deduplicated notification. */
    public static boolean syncBarrier(
            MinecraftServer server,
            ServantEncounterData.LiveVictory barrier) {
        boolean legacyApplied = syncLegacyBarrier(server, barrier);
        StoryService.SubmissionResult story = submitStoryBarrier(server, barrier);
        return legacyApplied || story.status() == StoryService.SubmissionStatus.APPLIED;
    }

    /** Compatibility-only side effect; never owns typed story progression. */
    public static boolean syncLegacyBarrier(
            MinecraftServer server,
            ServantEncounterData.LiveVictory barrier) {
        HeraldorWorldData worldData = HeraldorWorldData.get(server);
        boolean legacyApplied = false;
        try {
            legacyApplied = worldData.recordVictory(
                    barrier.targetId(), barrier.encounterId());
        } catch (IllegalStateException unsupportedSchema) {
            ZapeGRuntime.LOGGER.error(
                    "Legacy Servant counter unavailable encounter={} target={} reason={}",
                    barrier.encounterId(),
                    barrier.targetId(),
                    unsupportedSchema.getMessage());
        }
        if (!legacyApplied) {
            ZapeGRuntime.LOGGER.debug(
                    "Legacy Servant barrier already synchronized encounter={} target={}",
                    barrier.encounterId(),
                    barrier.targetId());
        }

        if (legacyApplied) {
            int victories = victoryCount(server, barrier.targetId());
            ZapeGRuntime.LOGGER.info(
                    "Servant live victory synchronized encounter={} target={} victories={}",
                    barrier.encounterId(),
                    barrier.targetId(),
                    victories);
            // Advisory compatibility event only. Story authority consumes the
            // durable barrier directly below and never trusts this event.
            MinecraftForge.EVENT_BUS.post(new ServantVictoryEvent(
                    server,
                    barrier.encounterId(),
                    barrier.targetId(),
                    victories,
                    barrier.archetype()));
        }

        return legacyApplied;
    }

    /** Typed story submission shared by immediate death and ordered reconciliation. */
    public static StoryService.SubmissionResult submitStoryBarrier(
            MinecraftServer server,
            ServantEncounterData.LiveVictory barrier) {
        StoryService.SubmissionResult story = StoryService.submitIfExpected(
                server,
                barrier.encounterId(),
                barrier.targetId(),
                StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                StoryFactType.SERVANT_DEFEATED,
                storySubject(barrier.archetype()));
        switch (story.status()) {
            case APPLIED -> ZapeGRuntime.LOGGER.info(
                    "Servant story barrier applied encounter={} target={} archetype={}",
                    barrier.encounterId(), barrier.targetId(), barrier.archetype().id());
            case ALREADY_PROCESSED -> ZapeGRuntime.LOGGER.debug(
                    "Servant story barrier already applied encounter={} target={}",
                    barrier.encounterId(), barrier.targetId());
            case NOT_EXPECTED -> ZapeGRuntime.LOGGER.debug(
                    "Servant story barrier retained for later encounter={} target={} archetype={}",
                    barrier.encounterId(), barrier.targetId(), barrier.archetype().id());
            case FACT_ID_CONFLICT -> ZapeGRuntime.LOGGER.error(
                    "Servant story barrier identity conflict encounter={} target={} archetype={}",
                    barrier.encounterId(), barrier.targetId(), barrier.archetype().id());
            default -> ZapeGRuntime.LOGGER.warn(
                    "Servant story barrier deferred encounter={} target={} archetype={} status={}",
                    barrier.encounterId(),
                    barrier.targetId(),
                    barrier.archetype().id(),
                    story.status());
        }
        return story;
    }

    public static ResourceLocation storySubject(ServantArchetype archetype) {
        return ResourceLocation.fromNamespaceAndPath(
                ZapeGRuntime.MOD_ID, archetype.id() + "_01");
    }

    /** Returns -1 when a future campaign schema is intentionally read-only. */
    public static int victoryCount(MinecraftServer server, UUID targetId) {
        return HeraldorWorldData.get(server)
                .snapshotForDiagnostics(targetId)
                .map(HeraldorWorldData.PlayerSnapshot::victories)
                .orElse(-1);
    }
}
