package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted once after a non-rehearsal victory has been durably consumed by the
 * SavedData ledger. Campaign code may observe this event to advance a node.
 */
public final class ServantVictoryEvent extends Event {

    private final MinecraftServer server;
    private final UUID encounterId;
    private final UUID targetId;
    private final int victoryCount;

    public ServantVictoryEvent(
            MinecraftServer server,
            UUID encounterId,
            UUID targetId,
            int victoryCount) {
        this.server = server;
        this.encounterId = encounterId;
        this.targetId = targetId;
        this.victoryCount = victoryCount;
    }

    public MinecraftServer server() {
        return server;
    }

    public UUID encounterId() {
        return encounterId;
    }

    public UUID targetId() {
        return targetId;
    }

    public int victoryCount() {
        return victoryCount;
    }
}
