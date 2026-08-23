package io.github.mizar107.zapegruntime.story;

import java.util.Objects;

/** Pure deterministic transition evaluator. It performs no world or chunk access. */
public final class StoryTransitionEngine {

    private StoryTransitionEngine() {}

    public static Decision evaluate(
            StoryCampaignDefinition campaign,
            String currentNodeId,
            long currentProgressEpoch,
            StoryFact fact) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(currentNodeId, "currentNodeId");
        Objects.requireNonNull(fact, "fact");
        if (!campaign.id().equals(fact.campaignId())
                || campaign.revision() != fact.campaignRevision()) {
            return new Decision(Outcome.DEFINITION_MISMATCH, currentNodeId);
        }
        if (currentProgressEpoch != fact.progressEpoch()) {
            return new Decision(Outcome.STALE_EPOCH, currentNodeId);
        }
        StoryNode current = campaign.node(currentNodeId);
        if (current == null) {
            return new Decision(Outcome.INVALID_STATE, currentNodeId);
        }
        if (!current.id().equals(fact.expectedNodeId())) {
            return new Decision(Outcome.STALE_NODE, currentNodeId);
        }
        if (current.terminal()) {
            return new Decision(Outcome.TERMINAL, currentNodeId);
        }
        if (!current.advanceOn().equals(fact.trigger())) {
            return new Decision(Outcome.NO_MATCH, currentNodeId);
        }
        return new Decision(Outcome.ADVANCE, current.nextNodeId());
    }

    public enum Outcome {
        ADVANCE,
        NO_MATCH,
        STALE_NODE,
        TERMINAL,
        STALE_EPOCH,
        DEFINITION_MISMATCH,
        INVALID_STATE
    }

    public record Decision(Outcome outcome, String resultingNodeId) {
        public Decision {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(resultingNodeId, "resultingNodeId");
        }
    }
}
