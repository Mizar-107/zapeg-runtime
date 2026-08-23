package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.server.HeraldorWorldData;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

/** Replays permanent Servant barriers into the sole campaign authority. */
public final class ServantProgressionSync {

    private ServantProgressionSync() {}

    /**
     * Replays every durable barrier after startup. Keeping the Servant-side
     * barrier permanently makes either SavedData save order crash-safe: a
     * missing campaign write is retried, while an existing event UUID is a
     * no-op in {@link HeraldorWorldData}.
     */
    public static void replayAll(MinecraftServer server) {
        for (ServantEncounterData.LiveVictory barrier
                : ServantEncounterData.get(server).liveVictories()) {
            syncBarrier(server, barrier);
        }
    }

    /** Applies one barrier and emits only an advisory, deduplicated notification. */
    public static boolean syncBarrier(
            MinecraftServer server,
            ServantEncounterData.LiveVictory barrier) {
        HeraldorWorldData worldData = HeraldorWorldData.get(server);
        final boolean applied;
        try {
            applied = worldData.recordVictory(barrier.targetId(), barrier.encounterId());
        } catch (IllegalStateException unsupportedSchema) {
            ZapeGRuntime.LOGGER.error(
                    "Deferred Servant victory sync encounter={} target={} reason={}",
                    barrier.encounterId(),
                    barrier.targetId(),
                    unsupportedSchema.getMessage());
            return false;
        }
        if (!applied) {
            ZapeGRuntime.LOGGER.debug(
                    "Servant victory barrier already synchronized encounter={} target={}",
                    barrier.encounterId(),
                    barrier.targetId());
            return false;
        }

        int victories = victoryCount(server, barrier.targetId());
        ZapeGRuntime.LOGGER.info(
                "Servant live victory synchronized encounter={} target={} victories={}",
                barrier.encounterId(),
                barrier.targetId(),
                victories);
        MinecraftForge.EVENT_BUS.post(new ServantVictoryEvent(
                server,
                barrier.encounterId(),
                barrier.targetId(),
                victories));
        return true;
    }

    /** Returns -1 when a future campaign schema is intentionally read-only. */
    public static int victoryCount(MinecraftServer server, UUID targetId) {
        return HeraldorWorldData.get(server)
                .snapshotForDiagnostics(targetId)
                .map(HeraldorWorldData.PlayerSnapshot::victories)
                .orElse(-1);
    }
}
