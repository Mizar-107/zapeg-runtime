package io.github.mizar107.zapegruntime.journal;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure authorization rules used before any journal view or discovery mutation. */
public final class JournalAuthorization {

    private JournalAuthorization() {}

    public static Optional<JournalView> viewFor(
            UUID senderId,
            StoryCampaignDefinition campaign,
            boolean storyDataWritable,
            Optional<StoryWorldData.PlayerSnapshot> state) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(state, "state");
        if (!storyDataWritable) {
            return Optional.empty();
        }
        if (state.isEmpty()) {
            // No player record means pristine onboarding, never arbitrary node zero.
            return campaign.ordinalOf(campaign.entryNodeId()) == 0
                    ? Optional.of(JournalView.through(0))
                    : Optional.empty();
        }
        StoryWorldData.PlayerSnapshot snapshot = state.get();
        int ordinal = campaign.ordinalOf(snapshot.currentNodeId());
        if (!snapshot.playerId().equals(senderId)
                || !snapshot.campaignId().equals(campaign.id())
                || snapshot.campaignRevision() != campaign.revision()
                || !snapshot.definitionFingerprint().equals(campaign.fingerprint())
                || ordinal < 0
                || ordinal >= JournalView.ENTRY_COUNT) {
            return Optional.empty();
        }
        return Optional.of(JournalView.through(ordinal));
    }

    public static ActionDecision actionFor(
            UUID senderId,
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> state,
            JournalAction action,
            boolean hasActiveJournal) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(action, "action");
        if (!hasActiveJournal) {
            return ActionDecision.NO_POSSESSION;
        }
        if (state.isEmpty()) {
            return ActionDecision.NOT_EXPECTED;
        }
        StoryWorldData.PlayerSnapshot snapshot = state.get();
        StoryNode node = campaign.node(action.expectedNodeId());
        if (!snapshot.playerId().equals(senderId)
                || !snapshot.campaignId().equals(campaign.id())
                || snapshot.campaignRevision() != campaign.revision()
                || !snapshot.definitionFingerprint().equals(campaign.fingerprint())) {
            return ActionDecision.STATE_MISMATCH;
        }
        if (!snapshot.currentNodeId().equals(action.expectedNodeId())
                || campaign.ordinalOf(snapshot.currentNodeId()) != action.entryOrdinal()
                || node == null
                || node.advanceOn() == null
                || node.advanceOn().type() != StoryFactType.JOURNAL_DISCOVERY
                || !node.advanceOn().subject().equals(action.subject())) {
            return ActionDecision.NOT_EXPECTED;
        }
        return ActionDecision.ALLOW;
    }

    public enum ActionDecision {
        ALLOW,
        NO_POSSESSION,
        NOT_EXPECTED,
        STATE_MISMATCH
    }
}
