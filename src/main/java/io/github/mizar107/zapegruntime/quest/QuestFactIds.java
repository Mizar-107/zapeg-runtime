package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Stable replay keys for player-authored quest evidence. */
public final class QuestFactIds {

    private static final String VERSION = "quest-fact-v1";

    private QuestFactIds() {}

    public static UUID forAction(UUID playerId, long recoveryEpoch, QuestAction action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        if (recoveryEpoch < 0L) {
            throw new IllegalArgumentException("recovery epoch must be non-negative");
        }
        String canonical = String.join(
                "\n",
                VERSION,
                StoryCampaignRegistry.HERALDOR_CAMPAIGN.toString(),
                playerId.toString(),
                Long.toString(recoveryEpoch),
                action.factType().serializedName(),
                action.subject().toString());
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
    }
}
