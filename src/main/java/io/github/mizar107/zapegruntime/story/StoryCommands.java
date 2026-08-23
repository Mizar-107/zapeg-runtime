package io.github.mizar107.zapegruntime.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.mizar107.zapegruntime.ZapeGRuntime;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** UUID-only operator diagnostics and explicit idempotent recovery. */
public final class StoryCommands {

    private StoryCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("story")
                        .then(Commands.literal("status")
                                .then(Commands.argument("target_uuid", UuidArgument.uuid())
                                        .executes(StoryCommands::status)))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("target_uuid", UuidArgument.uuid())
                                        .then(Commands.argument(
                                                        "operation_id", UuidArgument.uuid())
                                                .then(Commands.literal("reset")
                                                        .executes(context -> recover(
                                                                context, null)))
                                                .then(Commands.literal("node")
                                                        .then(Commands.argument(
                                                                        "node_id",
                                                                        StringArgumentType.word())
                                                                .executes(context -> recover(
                                                                        context,
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "node_id")))))))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        UUID playerId = UuidArgument.getUuid(context, "target_uuid");
        StoryCampaignRegistry.Snapshot registry = StoryCampaignRegistry.current();
        Optional<StoryCampaignDefinition> campaign =
                registry.find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        StoryWorldData data = StoryWorldData.get(context.getSource().getServer());
        StoryWorldData.SchemaStatus schema = data.schemaStatus();
        Optional<StoryWorldData.PlayerSnapshot> player = data.snapshot(playerId);
        String campaignDetail = campaign
                .map(value -> "campaign=" + value.id()
                        + " revision=" + value.revision()
                        + " fingerprint=" + value.fingerprint().substring(0, 12))
                .orElse("campaign=missing");
        String playerDetail = player
                .map(value -> "node=" + value.currentNodeId()
                        + " epoch=" + value.progressEpoch()
                        + " completed=" + value.completedNodes().size()
                        + "/29 facts=" + value.processedFactCount()
                        + "/" + StoryWorldData.MAX_PROCESSED_FACTS_PER_PLAYER
                        + " recoveries=" + value.recoveryOperationCount()
                        + "/" + StoryWorldData.MAX_RECOVERY_OPERATIONS_PER_PLAYER)
                .orElse("node=uninitialized");
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "story target_uuid=" + playerId
                                + " registry_generation=" + registry.generation()
                                + " " + campaignDetail
                                + " data_schema=" + schema.loadedVersion()
                                + "/" + schema.currentVersion()
                                + " data_health=" + schema.health()
                                + " writable=" + schema.writable()
                                + " detail=" + schema.detail()
                                + " " + playerDetail),
                false);
        return 1;
    }

    private static int recover(CommandContext<CommandSourceStack> context, String requestedNode) {
        UUID playerId = UuidArgument.getUuid(context, "target_uuid");
        UUID operationId = UuidArgument.getUuid(context, "operation_id");
        Optional<StoryCampaignDefinition> loaded = StoryCampaignRegistry.current()
                .find(StoryCampaignRegistry.HERALDOR_CAMPAIGN);
        if (loaded.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "Heraldor story recovery refused: campaign registry is unavailable"));
            audit(context, playerId, operationId, requestedNode, "CAMPAIGN_NOT_LOADED");
            return 0;
        }
        StoryCampaignDefinition campaign = loaded.get();
        String targetNode = requestedNode == null ? campaign.entryNodeId() : requestedNode;
        StoryWorldData.RecoveryResult result = StoryWorldData.get(
                        context.getSource().getServer())
                .recover(campaign, playerId, operationId, targetNode);
        Component reply = Component.literal(
                "story recovery=" + result.status()
                        + " target_uuid=" + playerId
                        + " node=" + (result.currentNodeId() == null
                                ? targetNode
                                : result.currentNodeId())
                        + " operation=" + operationId
                        + " detail=" + result.detail());
        boolean success = result.status() == StoryWorldData.RecoveryStatus.RESET
                || result.status() == StoryWorldData.RecoveryStatus.MOVED
                || result.status() == StoryWorldData.RecoveryStatus.DUPLICATE;
        if (success) {
            context.getSource().sendSuccess(() -> reply, false);
        } else {
            context.getSource().sendFailure(reply);
        }
        audit(context, playerId, operationId, targetNode, result.status().name());
        return success ? 1 : 0;
    }

    private static void audit(
            CommandContext<CommandSourceStack> context,
            UUID playerId,
            UUID operationId,
            String nodeId,
            String result) {
        ZapeGRuntime.LOGGER.info(
                "Story operator action source={} target_uuid={} operation={} node={} result={}",
                context.getSource().getTextName(),
                playerId,
                operationId,
                nodeId,
                result);
    }
}
