package io.github.mizar107.zapegruntime.quest;

import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryService;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Read-only exact-node gate in front of every quest sample and submission. */
final class QuestStoryAccess {

    private QuestStoryAccess() {}

    static Optional<ExpectedAction> expected(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "player server");
        Optional<StoryCampaignDefinition> campaign =
                StoryCampaignRegistry.current().find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        if (campaign.isEmpty()) {
            return Optional.empty();
        }
        StoryWorldData data = StoryWorldData.get(server);
        boolean writable = data.schemaStatus().writable();
        return resolve(campaign.get(), data.snapshot(player.getUUID()), player.getUUID(), writable);
    }

    static Optional<ExpectedAction> resolve(
            StoryCampaignDefinition campaign,
            Optional<StoryWorldData.PlayerSnapshot> persisted,
            UUID playerId,
            boolean writable) {
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(persisted, "persisted");
        Objects.requireNonNull(playerId, "playerId");
        if (!writable) {
            return Optional.empty();
        }

        String nodeId = campaign.entryNodeId();
        long epoch = 0L;
        if (persisted.isPresent()) {
            StoryWorldData.PlayerSnapshot state = persisted.get();
            if (!state.playerId().equals(playerId)
                    || !state.campaignId().equals(campaign.id())
                    || state.campaignRevision() != campaign.revision()
                    || !state.definitionFingerprint().equals(campaign.fingerprint())) {
                return Optional.empty();
            }
            nodeId = state.currentNodeId();
            epoch = state.progressEpoch();
        }

        StoryNode node = campaign.node(nodeId);
        if (node == null || node.terminal() || node.advanceOn() == null) {
            return Optional.empty();
        }
        long recoveryEpoch = epoch;
        return QuestAction.forTrigger(node.advanceOn())
                .map(action -> new ExpectedAction(action, recoveryEpoch, node.id()));
    }

    static StoryService.SubmissionResult submit(
            ServerPlayer player, ExpectedAction expected) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(expected, "expected");
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "player server");
        QuestAction action = expected.action();
        return StoryService.submitIfExpected(
                server,
                QuestFactIds.forAction(player.getUUID(), expected.recoveryEpoch(), action),
                player.getUUID(),
                StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                action.factType(),
                action.subject());
    }

    record ExpectedAction(QuestAction action, long recoveryEpoch, String nodeId) {

        ExpectedAction {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(nodeId, "nodeId");
            if (recoveryEpoch < 0L || nodeId.isBlank()) {
                throw new IllegalArgumentException("expected action context is invalid");
            }
        }
    }
}
