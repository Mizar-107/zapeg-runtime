package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Pure exact gate for the two boss-owned campaign ordinals. */
public final class NinthFormStoryGate {

    public static final int FIRST_SHAPE_ORDINAL = 27;
    public static final int LAST_SHAPE_ORDINAL = 28;
    public static final String FIRST_SHAPE_NODE = "first_shape";
    public static final String LAST_SHAPE_NODE = "last_shape";
    public static final String AFTER_NINTH_NODE = "after_ninth";
    public static final ResourceLocation PHASE_SUBJECT = Objects.requireNonNull(
            ResourceLocation.tryBuild("zapeg_runtime", "ninth_form_phase_01"));
    public static final ResourceLocation DEFEAT_SUBJECT = Objects.requireNonNull(
            ResourceLocation.tryBuild("zapeg_runtime", "ninth_form"));

    private NinthFormStoryGate() {}

    public static Decision forStart(
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> snapshot) {
        return evaluate(campaign, snapshot, FIRST_SHAPE_NODE, FIRST_SHAPE_ORDINAL);
    }

    public static Decision forFinalPhase(
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> snapshot,
            NinthFormEncounter encounter) {
        Objects.requireNonNull(encounter, "encounter");
        Decision decision = evaluate(campaign, snapshot, LAST_SHAPE_NODE, LAST_SHAPE_ORDINAL);
        if (decision.status() != Status.ELIGIBLE) {
            return decision;
        }
        Envelope envelope = decision.envelope().orElseThrow();
        if (!envelope.matches(encounter)) {
            return new Decision(
                    Status.ENVELOPE_MISMATCH,
                    "story recovery or datapack reload changed the encounter envelope",
                    Optional.empty());
        }
        return decision;
    }

    public static boolean definitionIsExact(StoryCampaignDefinition campaign) {
        if (campaign == null || !campaign.id().equals(StoryCampaignRegistry.HERALDOR_CAMPAIGN)) {
            return false;
        }
        StoryNode first = campaign.node(FIRST_SHAPE_NODE);
        StoryNode last = campaign.node(LAST_SHAPE_NODE);
        StoryNode after = campaign.node(AFTER_NINTH_NODE);
        return first != null
                && first.ordinal() == FIRST_SHAPE_ORDINAL
                && first.advanceOn() != null
                && first.advanceOn().type() == StoryFactType.BOSS_PHASE_COMPLETED
                && first.advanceOn().subject().equals(PHASE_SUBJECT)
                && LAST_SHAPE_NODE.equals(first.nextNodeId())
                && last != null
                && last.ordinal() == LAST_SHAPE_ORDINAL
                && last.advanceOn() != null
                && last.advanceOn().type() == StoryFactType.BOSS_DEFEATED
                && last.advanceOn().subject().equals(DEFEAT_SUBJECT)
                && AFTER_NINTH_NODE.equals(last.nextNodeId())
                && after != null
                && after.ordinal() == 29
                && after.terminal();
    }

    private static Decision evaluate(
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> snapshot,
            String expectedNode,
            int expectedOrdinal) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!definitionIsExact(campaign)) {
            return new Decision(
                    Status.DEFINITION_MISMATCH,
                    "loaded campaign does not expose the exact ordinal 27/28 boss contract",
                    Optional.empty());
        }
        if (snapshot.isEmpty()) {
            return new Decision(
                    Status.STATE_NOT_READY,
                    "player has no writable campaign snapshot",
                    Optional.empty());
        }
        StoryWorldData.PlayerSnapshot state = snapshot.get();
        if (!state.campaignId().equals(campaign.id())
                || state.campaignRevision() != campaign.revision()
                || !state.definitionFingerprint().equals(campaign.fingerprint())
                || campaign.ordinalOf(state.currentNodeId()) != expectedOrdinal
                || !expectedNode.equals(state.currentNodeId())) {
            return new Decision(
                    Status.NOT_EXPECTED,
                    "player is not at the exact boss-owned campaign node",
                    Optional.empty());
        }
        return new Decision(
                Status.ELIGIBLE,
                "campaign envelope and boss ordinal match",
                Optional.of(new Envelope(
                        campaign.id(),
                        campaign.revision(),
                        campaign.fingerprint(),
                        state.progressEpoch())));
    }

    public enum Status {
        ELIGIBLE,
        NOT_EXPECTED,
        STATE_NOT_READY,
        DEFINITION_MISMATCH,
        ENVELOPE_MISMATCH
    }

    public record Decision(Status status, String detail, Optional<Envelope> envelope) {
        public Decision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(envelope, "envelope");
        }
    }

    public record Envelope(
            ResourceLocation campaignId,
            int campaignRevision,
            String campaignFingerprint,
            long progressEpoch) {

        public Envelope {
            Objects.requireNonNull(campaignId, "campaignId");
            Objects.requireNonNull(campaignFingerprint, "campaignFingerprint");
            if (campaignRevision < 1 || progressEpoch < 0L) {
                throw new IllegalArgumentException("invalid campaign envelope");
            }
        }

        public boolean matches(NinthFormEncounter encounter) {
            return campaignId.equals(encounter.campaignId())
                    && campaignRevision == encounter.campaignRevision()
                    && campaignFingerprint.equals(encounter.campaignFingerprint())
                    && progressEpoch == encounter.progressEpoch();
        }
    }
}
