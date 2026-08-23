package io.github.mizar107.zapegruntime.story;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure gate which turns an expected durable barrier into an epoch-bound fact. */
public final class StoryFactGate {

    private StoryFactGate() {}

    public static Decision prepare(
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> persisted,
            UUID factId,
            UUID playerId,
            StoryFactType type,
            ResourceLocation subject) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(persisted, "persisted");
        Objects.requireNonNull(factId, "factId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");

        String currentNodeId = campaign.entryNodeId();
        long progressEpoch = 0L;
        if (persisted.isPresent()) {
            StoryWorldData.PlayerSnapshot state = persisted.get();
            if (!state.playerId().equals(playerId)
                    || !state.campaignId().equals(campaign.id())
                    || state.campaignRevision() != campaign.revision()
                    || !state.definitionFingerprint().equals(campaign.fingerprint())) {
                return Decision.notReady("player state is not bound to this campaign definition");
            }
            currentNodeId = state.currentNodeId();
            progressEpoch = state.progressEpoch();
        }

        StoryNode current = campaign.node(currentNodeId);
        if (current == null) {
            return Decision.notReady("player current node is absent from the campaign");
        }
        StoryTrigger offered = new StoryTrigger(type, subject);
        if (current.terminal() || !offered.equals(current.advanceOn())) {
            return Decision.notExpected("durable fact is not expected by the current node");
        }
        return Decision.ready(new StoryFact(
                factId,
                playerId,
                campaign.id(),
                campaign.revision(),
                progressEpoch,
                current.id(),
                type,
                subject));
    }

    public enum Outcome {
        READY,
        NOT_EXPECTED,
        STATE_NOT_READY
    }

    public record Decision(Outcome outcome, String detail, Optional<StoryFact> fact) {

        public Decision {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(fact, "fact");
            if ((outcome == Outcome.READY) != fact.isPresent()) {
                throw new IllegalArgumentException("only READY decisions carry a fact");
            }
        }

        private static Decision ready(StoryFact fact) {
            return new Decision(Outcome.READY, "typed fact is expected", Optional.of(fact));
        }

        private static Decision notExpected(String detail) {
            return new Decision(Outcome.NOT_EXPECTED, detail, Optional.empty());
        }

        private static Decision notReady(String detail) {
            return new Decision(Outcome.STATE_NOT_READY, detail, Optional.empty());
        }
    }
}
