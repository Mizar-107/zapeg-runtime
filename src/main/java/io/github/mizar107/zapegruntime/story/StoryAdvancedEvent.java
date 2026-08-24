package io.github.mizar107.zapegruntime.story;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Neutral post-commit signal; listeners may enqueue work but must not mutate inline. */
public final class StoryAdvancedEvent extends Event {

    private final MinecraftServer server;
    private final UUID playerId;
    private final UUID factId;
    private final StoryFactType factType;
    private final ResourceLocation subject;
    private final String previousNodeId;
    private final String currentNodeId;

    public StoryAdvancedEvent(
            MinecraftServer server,
            UUID playerId,
            UUID factId,
            StoryFactType factType,
            ResourceLocation subject,
            String previousNodeId,
            String currentNodeId) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.factId = Objects.requireNonNull(factId, "factId");
        this.factType = Objects.requireNonNull(factType, "factType");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.previousNodeId = Objects.requireNonNull(previousNodeId, "previousNodeId");
        this.currentNodeId = Objects.requireNonNull(currentNodeId, "currentNodeId");
        if (previousNodeId.equals(currentNodeId)) {
            throw new IllegalArgumentException("story advance must change nodes");
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID factId() {
        return factId;
    }

    public StoryFactType factType() {
        return factType;
    }

    public ResourceLocation subject() {
        return subject;
    }

    public String previousNodeId() {
        return previousNodeId;
    }

    public String currentNodeId() {
        return currentNodeId;
    }
}
