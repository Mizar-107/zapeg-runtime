package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Advisory, at-least-once notification that a durable live-victory barrier exists.
 *
 * <p>This Forge event is not a transactional outbox and gameplay must not
 * depend on receiving it. It may be delivered again during integration
 * reconciliation; optional listeners must deduplicate by {@link #encounterId()}.
 * Authoritative integration replays {@link ServantEncounterData#liveVictories()}
 * into world state idempotently.</p>
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
