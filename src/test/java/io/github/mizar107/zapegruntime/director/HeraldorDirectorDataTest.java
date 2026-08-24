package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignJsonParser;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import io.github.mizar107.zapegruntime.story.StoryWorldData;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class HeraldorDirectorDataTest {

    private static final UUID TARGET =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final ResourceLocation CAMPAIGN_ID = id("heraldor");

    @Test
    void stablePreparedEventSurvivesSaveAndAwaitingRestartWindow() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        HeraldorDirectorData.PlanResult first = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 500L, false);
        assertEquals(HeraldorDirectorData.PlanStatus.DISPATCH, first.status());
        UUID eventId = first.record().eventId();
        assertTrue(data.markAwaiting(first.record().identity(), 900L));

        HeraldorDirectorData loaded = HeraldorDirectorData.load(data.save(new CompoundTag()));
        assertEquals(eventId, loaded.record(TARGET).orElseThrow().eventId());
        assertEquals(
                HeraldorDirectorData.PlanStatus.WAITING,
                loaded.plan(
                        fixture.campaign(),
                        fixture.snapshot(),
                        fixture.node(),
                        fixture.binding(),
                        899L,
                        false).status());
        HeraldorDirectorData.PlanResult rotated = loaded.plan(
                fixture.campaign(),
                fixture.snapshot(),
                fixture.node(),
                fixture.binding(),
                900L,
                false);
        assertEquals(HeraldorDirectorData.PlanStatus.DISPATCH, rotated.status());
        assertEquals(1, rotated.record().attempt());
        assertNotEquals(eventId, rotated.record().eventId());
    }

    @Test
    void liveExactScenePreventsAwaitingRedispatchPastDeadline() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        HeraldorDirectorData.DispatchRecord record = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 10L, false)
                .record();
        assertTrue(data.markAwaiting(record.identity(), 20L));
        assertEquals(
                HeraldorDirectorData.PlanStatus.WAITING,
                data.plan(
                        fixture.campaign(),
                        fixture.snapshot(),
                        fixture.node(),
                        fixture.binding(),
                        1000L,
                        true).status());
        assertEquals(record.eventId(), data.record(TARGET).orElseThrow().eventId());
    }

    @Test
    void proofIsDurableBeforeStoryAndNeverRedispatches() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        HeraldorDirectorData.DispatchRecord record = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 10L, false)
                .record();
        assertTrue(data.markAwaiting(record.identity(), 200L));
        assertTrue(data.markProven(
                record.identity(), DirectorPresentationPolicy.Proof.TIMEOUT, 40L));

        HeraldorDirectorData loaded = HeraldorDirectorData.load(data.save(new CompoundTag()));
        HeraldorDirectorData.DispatchRecord proof = loaded.record(TARGET).orElseThrow();
        assertEquals(HeraldorDirectorData.DispatchState.PROVEN, proof.state());
        assertEquals(DirectorPresentationPolicy.Proof.TIMEOUT, proof.proof());
        assertEquals(
                HeraldorDirectorData.PlanStatus.PROOF_READY,
                loaded.plan(
                        fixture.campaign(),
                        fixture.snapshot(),
                        fixture.node(),
                        fixture.binding(),
                        5_000L,
                        false).status());
    }

    @Test
    void unconsumedBackoffKeepsEventButConsumedFailureRotatesIt() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        HeraldorDirectorData.DispatchRecord record = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 0L, false)
                .record();
        assertTrue(data.markBackoff(record.identity(), 100L, "no_valid_loaded_scene_anchor"));
        assertEquals(record.eventId(), data.record(TARGET).orElseThrow().eventId());
        assertTrue(data.markFailure(record.identity(), 200L, "event_id_is_already_consumed"));
        HeraldorDirectorData.DispatchRecord rotated = data.record(TARGET).orElseThrow();
        assertEquals(1, rotated.attempt());
        assertNotEquals(record.eventId(), rotated.eventId());
    }

    @Test
    void definitionMismatchBlocksSameEpochUntilExplicitStoryRecovery() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        HeraldorDirectorData.DispatchRecord record = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 0L, false)
                .record();
        assertTrue(data.markBlocked(record.identity(), "binding_definition_mismatch"));
        DirectorSceneBinding changed = new DirectorSceneBinding(
                fixture.binding().factType(),
                fixture.binding().subject(),
                fixture.binding().profile(),
                fixture.binding().ttlTicks(),
                fixture.binding().stage(),
                fixture.binding().presentationVariant(),
                fixture.binding().cooldownTicks() + 100,
                fixture.binding().retryTicks());
        HeraldorDirectorData.PlanResult blocked = data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), changed, 10_000L, false);
        assertEquals(HeraldorDirectorData.PlanStatus.BLOCKED, blocked.status());
        assertEquals(record.eventId(), blocked.record().eventId());

        Fixture recovered = fixture(1L);
        HeraldorDirectorData.PlanResult fresh = data.plan(
                recovered.campaign(),
                recovered.snapshot(),
                recovered.node(),
                changed,
                10_000L,
                false);
        assertEquals(HeraldorDirectorData.PlanStatus.DISPATCH, fresh.status());
        assertNotEquals(record.eventId(), fresh.record().eventId());
    }

    @Test
    void corruptAndFutureSchemasArePreservedReadOnly() {
        CompoundTag corruptRoot = new CompoundTag();
        corruptRoot.putInt("SchemaVersion", 1);
        corruptRoot.put("Records", new ListTag());
        corruptRoot.putString("Unexpected", "preserve");
        HeraldorDirectorData corrupt = HeraldorDirectorData.load(corruptRoot);
        assertEquals(HeraldorDirectorData.DataHealth.CORRUPT,
                corrupt.schemaStatus().health());
        assertFalse(corrupt.schemaStatus().writable());
        assertEquals(corruptRoot, corrupt.save(new CompoundTag()));

        CompoundTag futureRoot = new CompoundTag();
        futureRoot.putInt("SchemaVersion", 99);
        futureRoot.putString("Future", "untouched");
        HeraldorDirectorData future = HeraldorDirectorData.load(futureRoot);
        assertEquals(HeraldorDirectorData.DataHealth.UNSUPPORTED,
                future.schemaStatus().health());
        assertFalse(future.schemaStatus().writable());
        assertEquals(futureRoot, future.save(new CompoundTag()));
    }

    @Test
    void wrongListElementTypeAndUnknownRecordFieldFailClosed() {
        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt("SchemaVersion", 1);
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("not-a-record"));
        wrongType.put("Records", strings);
        assertEquals(
                HeraldorDirectorData.DataHealth.CORRUPT,
                HeraldorDirectorData.load(wrongType).schemaStatus().health());

        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        data.plan(
                fixture.campaign(), fixture.snapshot(), fixture.node(), fixture.binding(), 0L, false);
        CompoundTag encoded = data.save(new CompoundTag());
        encoded.getList("Records", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putBoolean("Unexpected", true);
        assertEquals(
                HeraldorDirectorData.DataHealth.CORRUPT,
                HeraldorDirectorData.load(encoded).schemaStatus().health());
    }

    @Test
    void targetCapacityRefusesNewStateWithoutEviction() {
        Fixture fixture = fixture(0L);
        HeraldorDirectorData data = new HeraldorDirectorData();
        UUID firstTarget = null;
        UUID firstEvent = null;
        for (int index = 0; index < HeraldorDirectorData.MAX_TARGETS; index++) {
            UUID target = UUID.nameUUIDFromBytes(("director-target-" + index)
                    .getBytes(StandardCharsets.UTF_8));
            StoryWorldData.PlayerSnapshot snapshot = snapshotFor(
                    fixture.campaign(), fixture.node(), target, 0L);
            HeraldorDirectorData.PlanResult result = data.plan(
                    fixture.campaign(), snapshot, fixture.node(), fixture.binding(), 0L, false);
            assertEquals(HeraldorDirectorData.PlanStatus.DISPATCH, result.status());
            if (index == 0) {
                firstTarget = target;
                firstEvent = result.record().eventId();
            }
        }
        UUID overflowTarget = UUID.nameUUIDFromBytes(
                "director-overflow".getBytes(StandardCharsets.UTF_8));
        HeraldorDirectorData.PlanResult overflow = data.plan(
                fixture.campaign(),
                snapshotFor(fixture.campaign(), fixture.node(), overflowTarget, 0L),
                fixture.node(),
                fixture.binding(),
                0L,
                false);
        assertEquals(HeraldorDirectorData.PlanStatus.CAPACITY_EXHAUSTED, overflow.status());
        assertEquals(firstEvent, data.record(firstTarget).orElseThrow().eventId());
        assertTrue(data.record(overflowTarget).isEmpty());
    }

    private static Fixture fixture(long epoch) {
        StoryCampaignDefinition campaign = packagedCampaign();
        StoryNode node = campaign.node("voice_without_air");
        DirectorSceneBinding binding = packagedCatalog().find(node.advanceOn()).orElseThrow();
        StoryWorldData.PlayerSnapshot snapshot = snapshotFor(campaign, node, TARGET, epoch);
        return new Fixture(campaign, node, binding, snapshot);
    }

    private static StoryWorldData.PlayerSnapshot snapshotFor(
            StoryCampaignDefinition campaign, StoryNode node, UUID target, long epoch) {
        return new StoryWorldData.PlayerSnapshot(
                target,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                epoch,
                node.id(),
                campaign.completedPrefixFor(node.id()),
                0,
                epoch == 0L ? 0 : 1);
    }

    private static StoryCampaignDefinition packagedCampaign() {
        try (var stream = HeraldorDirectorDataTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_story/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing campaign resource");
            }
            return StoryCampaignJsonParser.parse(
                    CAMPAIGN_ID,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static DirectorSceneCatalog packagedCatalog() {
        try (var stream = HeraldorDirectorDataTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_director/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing Director resource");
            }
            return DirectorSceneJsonParser.parse(
                    CAMPAIGN_ID,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }

    private record Fixture(
            StoryCampaignDefinition campaign,
            StoryNode node,
            DirectorSceneBinding binding,
            StoryWorldData.PlayerSnapshot snapshot) {}
}
