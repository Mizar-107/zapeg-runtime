package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class StoryWorldDataTest {

    private static final UUID PLAYER =
            UUID.fromString("00000000-0000-0000-0000-000000000107");

    @Test
    void transitionAndReplayBarrierRoundTripByUuid() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        StoryFact first = matchingFact(campaign, 0, uuid("first"));

        StoryWorldData.ApplyResult applied = data.applyFact(campaign, first);
        StoryWorldData.ApplyResult replay = data.applyFact(campaign, first);

        assertEquals(StoryWorldData.ApplyStatus.ADVANCED, applied.status());
        assertEquals("voice_without_air", applied.currentNodeId());
        assertEquals(StoryWorldData.ApplyStatus.DUPLICATE, replay.status());
        assertEquals("voice_without_air", replay.currentNodeId());

        StoryWorldData loaded = StoryWorldData.load(data.save(new CompoundTag()));
        StoryWorldData.PlayerSnapshot snapshot = loaded.snapshot(PLAYER).orElseThrow();
        assertEquals("voice_without_air", snapshot.currentNodeId());
        assertEquals(java.util.List.of("first_scratch"), snapshot.completedNodes());
        assertEquals(1, snapshot.processedFactCount());
        assertEquals(StoryWorldData.ApplyStatus.DUPLICATE,
                loaded.applyFact(campaign, first).status());
    }

    @Test
    void reusedFactUuidWithAnotherPayloadOrPlayerIsAConflictNotReplay() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        UUID factId = uuid("global-fact");
        StoryFact original = matchingFact(campaign, 0, factId);
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(campaign, original).status());

        StoryNode second = campaign.nodes().get(1);
        StoryFact mutatedPayload = new StoryFact(
                factId,
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                second.id(),
                second.advanceOn().type(),
                second.advanceOn().subject());
        assertEquals(
                StoryWorldData.ApplyStatus.FACT_ID_CONFLICT,
                data.applyFact(campaign, mutatedPayload).status());

        UUID anotherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000108");
        StoryNode entry = campaign.nodes().get(0);
        StoryFact retargeted = new StoryFact(
                factId,
                anotherPlayer,
                campaign.id(),
                campaign.revision(),
                0L,
                entry.id(),
                entry.advanceOn().type(),
                entry.advanceOn().subject());
        assertEquals(
                StoryWorldData.ApplyStatus.FACT_ID_CONFLICT,
                data.applyFact(campaign, retargeted).status());
        assertTrue(data.snapshot(anotherPlayer).isEmpty());
    }

    @Test
    void staleFactIsRecordedAndCannotBecomeValidLater() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        StoryFact earlySecond = matchingFact(campaign, 1, uuid("early-second"));

        assertEquals(
                StoryWorldData.ApplyStatus.RECORDED_STALE,
                data.applyFact(campaign, earlySecond).status());
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(campaign, matchingFact(campaign, 0, uuid("real-first"))).status());
        StoryWorldData.ApplyResult replay = data.applyFact(campaign, earlySecond);

        assertEquals(StoryWorldData.ApplyStatus.DUPLICATE, replay.status());
        assertEquals("voice_without_air", replay.currentNodeId());
    }

    @Test
    void nonMatchingFactsAreReceiptedWithoutProgress() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        StoryFact wrong = new StoryFact(
                uuid("wrong"),
                PLAYER,
                campaign.id(),
                campaign.revision(),
                0L,
                campaign.entryNodeId(),
                StoryFactType.SCENE_COMPLETED,
                StoryCampaignTestFixtures.id("unrelated"));

        assertEquals(
                StoryWorldData.ApplyStatus.RECORDED_NO_MATCH,
                data.applyFact(campaign, wrong).status());
        assertEquals("first_scratch", data.snapshot(PLAYER).orElseThrow().currentNodeId());
        assertEquals(1, data.snapshot(PLAYER).orElseThrow().processedFactCount());
        assertEquals(StoryWorldData.ApplyStatus.DUPLICATE,
                data.applyFact(campaign, wrong).status());
    }

    @Test
    void factLedgerIsBoundedAndFailsClosedAtCapacity() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        for (int index = 0; index < StoryWorldData.MAX_PROCESSED_FACTS_PER_PLAYER; index++) {
            StoryFact irrelevant = new StoryFact(
                    uuid("irrelevant-" + index),
                    PLAYER,
                    campaign.id(),
                    campaign.revision(),
                    0L,
                    campaign.entryNodeId(),
                    StoryFactType.SCENE_COMPLETED,
                    StoryCampaignTestFixtures.id("unrelated"));
            assertEquals(
                    StoryWorldData.ApplyStatus.RECORDED_NO_MATCH,
                    data.applyFact(campaign, irrelevant).status());
        }

        StoryWorldData.ApplyResult overflow = data.applyFact(
                campaign,
                new StoryFact(
                        uuid("overflow"),
                        PLAYER,
                        campaign.id(),
                        campaign.revision(),
                        0L,
                        campaign.entryNodeId(),
                        StoryFactType.SCENE_COMPLETED,
                        StoryCampaignTestFixtures.id("still_unrelated")));
        assertEquals(StoryWorldData.ApplyStatus.FACT_CAPACITY_EXHAUSTED, overflow.status());
        assertEquals(
                StoryWorldData.MAX_PROCESSED_FACTS_PER_PLAYER,
                data.snapshot(PLAYER).orElseThrow().processedFactCount());
    }

    @Test
    void idempotentRecoverySurvivesLaterProgressAndCanRebindDefinitions() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        UUID operation = uuid("recovery");

        assertEquals(
                StoryWorldData.RecoveryStatus.RESET,
                data.recover(campaign, PLAYER, operation, campaign.entryNodeId()).status());
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(
                                campaign,
                                matchingFact(campaign, 0, uuid("after-recovery"), 1L))
                        .status());
        StoryWorldData.RecoveryResult replay =
                data.recover(campaign, PLAYER, operation, campaign.entryNodeId());

        assertEquals(StoryWorldData.RecoveryStatus.DUPLICATE, replay.status());
        assertEquals("voice_without_air", replay.currentNodeId());

        StoryCampaignDefinition revisionTwo = new StoryCampaignDefinition(
                campaign.id(), 2, campaign.entryNodeId(), campaign.nodes());
        StoryFact revisionTwoFact = new StoryFact(
                uuid("revision-two"),
                PLAYER,
                revisionTwo.id(),
                revisionTwo.revision(),
                1L,
                revisionTwo.entryNodeId(),
                revisionTwo.nodes().get(0).advanceOn().type(),
                revisionTwo.nodes().get(0).advanceOn().subject());
        assertEquals(
                StoryWorldData.ApplyStatus.DEFINITION_MISMATCH,
                data.applyFact(revisionTwo, revisionTwoFact).status());
        assertEquals(
                StoryWorldData.RecoveryStatus.RESET,
                data.recover(
                                revisionTwo,
                                PLAYER,
                                uuid("rebind-two"),
                                revisionTwo.entryNodeId())
                        .status());
        assertEquals(2, data.snapshot(PLAYER).orElseThrow().campaignRevision());
        assertEquals(2L, data.snapshot(PLAYER).orElseThrow().progressEpoch());
    }

    @Test
    void recoveryPreservesReceiptsAndInvalidatesQueuedFactsFromTheOldEpoch() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        StoryFact preRecovery = matchingFact(campaign, 0, uuid("pre-recovery"), 0L);
        StoryFact queuedBeforeRecovery = matchingFact(
                campaign, 0, uuid("queued-before-recovery"), 0L);
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(campaign, preRecovery).status());

        assertEquals(
                StoryWorldData.RecoveryStatus.RESET,
                data.recover(
                                campaign,
                                PLAYER,
                                uuid("rotate-epoch"),
                                campaign.entryNodeId())
                        .status());
        StoryWorldData.PlayerSnapshot reset = data.snapshot(PLAYER).orElseThrow();
        assertEquals(1L, reset.progressEpoch());
        assertEquals(1, reset.processedFactCount());

        StoryWorldData.ApplyResult oldReplay = data.applyFact(campaign, preRecovery);
        assertEquals(StoryWorldData.ApplyStatus.DUPLICATE, oldReplay.status());
        assertEquals(campaign.entryNodeId(), oldReplay.currentNodeId());
        assertEquals(
                StoryWorldData.ApplyStatus.STALE_EPOCH,
                data.applyFact(campaign, queuedBeforeRecovery).status());
        assertEquals(
                StoryWorldData.ApplyStatus.ADVANCED,
                data.applyFact(
                                campaign,
                                matchingFact(campaign, 0, uuid("post-recovery"), 1L))
                        .status());
        assertTrue(data.hasProcessedFact(PLAYER, preRecovery.factId()).orElseThrow());
        assertFalse(data.hasProcessedFact(PLAYER, uuid("unknown-fact")).orElseThrow());
    }

    @Test
    void recoveryRepairsStructurallyValidButImpossiblePrefix() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData original = new StoryWorldData();
        original.applyFact(campaign, matchingFact(campaign, 0, uuid("advance")));
        CompoundTag root = original.save(new CompoundTag());
        root.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .put("CompletedNodes", new ListTag());
        StoryWorldData damaged = StoryWorldData.load(root);

        assertEquals(StoryWorldData.DataHealth.OK, damaged.schemaStatus().health());
        assertEquals(
                StoryWorldData.ApplyStatus.INVALID_STATE,
                damaged.applyFact(campaign, matchingFact(campaign, 1, uuid("blocked"))).status());
        assertEquals(
                StoryWorldData.RecoveryStatus.MOVED,
                damaged.recover(
                                campaign,
                                PLAYER,
                                uuid("repair-prefix"),
                                "voice_without_air")
                        .status());
        assertEquals(
                java.util.List.of("first_scratch"),
                damaged.snapshot(PLAYER).orElseThrow().completedNodes());
    }

    @Test
    void schemaOneMigratesAndAddsRecoveryLedger() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData current = new StoryWorldData();
        current.applyFact(campaign, matchingFact(campaign, 0, uuid("legacy-fact")));
        CompoundTag legacy = current.save(new CompoundTag());
        legacy.putInt("SchemaVersion", 1);
        CompoundTag legacyPlayer = legacy.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0);
        ListTag v2Receipts = legacyPlayer.getList(
                "ProcessedFacts", net.minecraft.nbt.Tag.TAG_COMPOUND);
        ListTag v1Receipts = new ListTag();
        for (int index = 0; index < v2Receipts.size(); index++) {
            v1Receipts.add(StringTag.valueOf(
                    v2Receipts.getCompound(index).getUUID("FactId").toString()));
        }
        legacyPlayer.put("ProcessedFacts", v1Receipts);
        legacyPlayer.remove("ProgressEpoch");
        legacyPlayer.remove("RecoveryOperations");

        StoryWorldData migrated = StoryWorldData.load(legacy);

        assertEquals(StoryWorldData.DataHealth.MIGRATED, migrated.schemaStatus().health());
        assertTrue(migrated.isDirty());
        assertEquals("voice_without_air", migrated.snapshot(PLAYER).orElseThrow().currentNodeId());
        assertEquals(0L, migrated.snapshot(PLAYER).orElseThrow().progressEpoch());
        CompoundTag upgraded = migrated.save(new CompoundTag());
        assertEquals(StoryWorldData.CURRENT_SCHEMA_VERSION, upgraded.getInt("SchemaVersion"));
        assertTrue(upgraded.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .contains("RecoveryOperations", net.minecraft.nbt.Tag.TAG_LIST));
    }

    @Test
    void emptySchemaZeroMigratesButUnversionedPayloadFailsClosed() {
        StoryWorldData empty = StoryWorldData.load(new CompoundTag());
        assertEquals(StoryWorldData.DataHealth.MIGRATED, empty.schemaStatus().health());
        assertTrue(empty.schemaStatus().writable());
        assertTrue(empty.isDirty());

        CompoundTag unknown = new CompoundTag();
        unknown.putString("Mystery", "preserve");
        StoryWorldData refused = StoryWorldData.load(unknown);
        assertEquals(StoryWorldData.DataHealth.CORRUPT, refused.schemaStatus().health());
        assertFalse(refused.schemaStatus().writable());
        assertEquals("preserve", refused.save(new CompoundTag()).getString("Mystery"));
    }

    @Test
    void currentSchemaCorruptionIsPreservedAndNeverPartiallyLoaded() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        data.applyFact(campaign, matchingFact(campaign, 0, uuid("valid-before-corruption")));
        CompoundTag corrupt = data.save(new CompoundTag());
        corrupt.putString("UnknownField", "must-survive");

        StoryWorldData loaded = StoryWorldData.load(corrupt);

        assertEquals(StoryWorldData.DataHealth.CORRUPT, loaded.schemaStatus().health());
        assertFalse(loaded.schemaStatus().writable());
        assertTrue(loaded.snapshot(PLAYER).isEmpty());
        assertEquals(
                StoryWorldData.ApplyStatus.DATA_UNAVAILABLE,
                loaded.applyFact(campaign, matchingFact(campaign, 1, uuid("cannot-apply"))).status());
        assertEquals("must-survive", loaded.save(new CompoundTag()).getString("UnknownField"));
    }

    @Test
    void wrongListElementTypeAndOversizedLedgerAreCorruption() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        data.applyFact(campaign, matchingFact(campaign, 0, uuid("seed")));

        CompoundTag wrongType = data.save(new CompoundTag());
        ListTag integers = new ListTag();
        integers.add(IntTag.valueOf(1));
        wrongType.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .put("ProcessedFacts", integers);
        assertEquals(
                StoryWorldData.DataHealth.CORRUPT,
                StoryWorldData.load(wrongType).schemaStatus().health());

        CompoundTag oversized = data.save(new CompoundTag());
        ListTag facts = new ListTag();
        for (int index = 0; index <= StoryWorldData.MAX_PROCESSED_FACTS_PER_PLAYER; index++) {
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("FactId", uuid("encoded-" + index));
            receipt.putString("Identity", "0".repeat(64));
            facts.add(receipt);
        }
        oversized.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .put("ProcessedFacts", facts);
        assertEquals(
                StoryWorldData.DataHealth.CORRUPT,
                StoryWorldData.load(oversized).schemaStatus().health());
    }

    @Test
    void duplicateFactUuidAcrossPersistedPlayersRejectsTheWholeRoot() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        data.applyFact(campaign, matchingFact(campaign, 0, uuid("duplicate-root-fact")));
        CompoundTag root = data.save(new CompoundTag());
        CompoundTag duplicatedPlayer = root.getList(
                        "Players", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .copy();
        duplicatedPlayer.putUUID(
                "PlayerId", UUID.fromString("00000000-0000-0000-0000-000000000109"));
        root.getList("Players", net.minecraft.nbt.Tag.TAG_COMPOUND).add(duplicatedPlayer);

        StoryWorldData loaded = StoryWorldData.load(root);
        assertEquals(StoryWorldData.DataHealth.CORRUPT, loaded.schemaStatus().health());
        assertFalse(loaded.schemaStatus().writable());
    }

    @Test
    void futureSchemaIsReadOnlyAndRoundTripsWithoutFieldLoss() {
        CompoundTag future = new CompoundTag();
        future.putInt("SchemaVersion", 99);
        future.putString("FuturePayload", "keep");

        StoryWorldData loaded = StoryWorldData.load(future);

        assertEquals(StoryWorldData.DataHealth.UNSUPPORTED, loaded.schemaStatus().health());
        assertFalse(loaded.schemaStatus().writable());
        assertEquals("keep", loaded.save(new CompoundTag()).getString("FuturePayload"));
    }

    private static StoryFact matchingFact(
            StoryCampaignDefinition campaign, int ordinal, UUID eventId) {
        return matchingFact(campaign, ordinal, eventId, 0L);
    }

    private static StoryFact matchingFact(
            StoryCampaignDefinition campaign, int ordinal, UUID eventId, long progressEpoch) {
        StoryNode node = campaign.nodes().get(ordinal);
        return new StoryFact(
                eventId,
                PLAYER,
                campaign.id(),
                campaign.revision(),
                progressEpoch,
                node.id(),
                node.advanceOn().type(),
                node.advanceOn().subject());
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
