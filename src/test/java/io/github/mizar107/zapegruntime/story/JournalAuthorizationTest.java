package io.github.mizar107.zapegruntime.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.journal.JournalAction;
import io.github.mizar107.zapegruntime.journal.JournalAuthorization;
import io.github.mizar107.zapegruntime.journal.JournalFactIdentity;
import io.github.mizar107.zapegruntime.journal.JournalView;
import io.github.mizar107.zapegruntime.journal.client.JournalClientText;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class JournalAuthorizationTest {

    private static final UUID PLAYER =
            UUID.fromString("05575801-d37c-30bf-ae2c-bb3a46d2be3e");

    @Test
    void absentPlayerStateAuthorizesOnlyTheDeclaredEntryOrdinalZero() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        assertEquals(0, campaign.ordinalOf(campaign.entryNodeId()));
        JournalView view = JournalAuthorization.viewFor(
                        PLAYER, campaign, true, Optional.empty())
                .orElseThrow();
        assertEquals(0, view.currentOrdinal());
        assertEquals(1, view.unlockedMask());
        assertTrue(view.unlocked(0));
        assertTrue(JournalAuthorization.viewFor(
                        PLAYER, campaign, false, Optional.empty())
                .isEmpty());
    }

    @Test
    void futureStoryRootCannotMasqueradeAsPristineOnboarding() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        CompoundTag futureRoot = new CompoundTag();
        futureRoot.putInt("SchemaVersion", StoryWorldData.CURRENT_SCHEMA_VERSION + 1);
        futureRoot.put("Players", new ListTag());
        StoryWorldData future = StoryWorldData.load(futureRoot);
        assertTrue(future.snapshot(PLAYER).isEmpty());
        assertTrue(!future.schemaStatus().writable());
        assertTrue(JournalAuthorization.viewFor(
                        PLAYER,
                        campaign,
                        future.schemaStatus().writable(),
                        future.snapshot(PLAYER))
                .isEmpty());
    }

    @Test
    void clientOrdinalTableMatchesAllThirtyAuthoritativeJournalKeys() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        assertEquals(30, JournalClientText.entryCount());
        campaign.nodes().forEach(node -> assertEquals(
                node.journalKey() + ".title",
                JournalClientText.titleKey(node.ordinal())));
    }

    @Test
    void establishedViewRequiresExactSenderAndDefinitionBinding() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData.PlayerSnapshot state = snapshot(campaign, "ledger_of_absence", 3L);
        JournalView view = JournalAuthorization.viewFor(
                        PLAYER, campaign, true, Optional.of(state))
                .orElseThrow();
        assertEquals(18, view.currentOrdinal());
        assertTrue(JournalAuthorization.viewFor(
                        UUID.randomUUID(), campaign, true, Optional.of(state))
                .isEmpty());
        StoryWorldData.PlayerSnapshot wrongFingerprint = new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                "0".repeat(64),
                3L,
                "ledger_of_absence",
                campaign.completedPrefixFor("ledger_of_absence"),
                0,
                0);
        assertTrue(JournalAuthorization.viewFor(
                        PLAYER, campaign, true, Optional.of(wrongFingerprint))
                .isEmpty());
    }

    @Test
    void journalDiscoveriesRequirePossessionSenderAndExactCurrentPredicate() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData.PlayerSnapshot palimpsest = snapshot(campaign, "ink_beneath_ink", 9L);
        assertEquals(
                JournalAuthorization.ActionDecision.ALLOW,
                JournalAuthorization.actionFor(
                        PLAYER,
                        campaign,
                        Optional.of(palimpsest),
                        JournalAction.REVEAL_PALIMPSEST,
                        true));
        assertEquals(
                JournalAuthorization.ActionDecision.NO_POSSESSION,
                JournalAuthorization.actionFor(
                        PLAYER,
                        campaign,
                        Optional.of(palimpsest),
                        JournalAction.REVEAL_PALIMPSEST,
                        false));
        assertEquals(
                JournalAuthorization.ActionDecision.STATE_MISMATCH,
                JournalAuthorization.actionFor(
                        UUID.randomUUID(),
                        campaign,
                        Optional.of(palimpsest),
                        JournalAction.REVEAL_PALIMPSEST,
                        true));
        assertEquals(
                JournalAuthorization.ActionDecision.NOT_EXPECTED,
                JournalAuthorization.actionFor(
                        PLAYER,
                        campaign,
                        Optional.of(palimpsest),
                        JournalAction.COUNT_ABSENCES,
                        true));
        assertEquals(
                JournalAuthorization.ActionDecision.NOT_EXPECTED,
                JournalAuthorization.actionFor(
                        PLAYER,
                        campaign,
                        Optional.empty(),
                        JournalAction.REVEAL_PALIMPSEST,
                        true));
    }

    @Test
    void appliedDiscoveryLeavesAnExactReceiptForPostAdvanceRetries() {
        StoryCampaignDefinition campaign = StoryCampaignTestFixtures.campaign();
        StoryWorldData data = new StoryWorldData();
        StoryWorldData.RecoveryResult moved = data.recover(
                campaign, PLAYER, UUID.randomUUID(), "ink_beneath_ink");
        assertEquals(StoryWorldData.RecoveryStatus.MOVED, moved.status());
        StoryWorldData.PlayerSnapshot before = data.snapshot(PLAYER).orElseThrow();
        UUID factId = JournalFactIdentity.derive(
                PLAYER,
                campaign.id(),
                before.progressEpoch(),
                JournalAction.REVEAL_PALIMPSEST.subject());
        StoryFact fact = StoryFactGate.prepare(
                        campaign,
                        Optional.of(before),
                        factId,
                        PLAYER,
                        StoryFactType.JOURNAL_DISCOVERY,
                        JournalAction.REVEAL_PALIMPSEST.subject())
                .fact()
                .orElseThrow();
        assertEquals(StoryWorldData.ApplyStatus.ADVANCED, data.applyFact(campaign, fact).status());
        assertEquals("name_refused", data.snapshot(PLAYER).orElseThrow().currentNodeId());
        assertEquals(
                StoryWorldData.ReceiptStatus.EXACT,
                data.receiptStatus(
                        PLAYER,
                        factId,
                        campaign.id(),
                        campaign.revision(),
                        StoryFactType.JOURNAL_DISCOVERY,
                        JournalAction.REVEAL_PALIMPSEST.subject()));
        assertEquals(
                StoryWorldData.ReceiptStatus.CONFLICT,
                data.receiptStatus(
                        PLAYER,
                        factId,
                        campaign.id(),
                        campaign.revision(),
                        StoryFactType.JOURNAL_DISCOVERY,
                        JournalAction.COUNT_ABSENCES.subject()));
    }

    private static StoryWorldData.PlayerSnapshot snapshot(
            StoryCampaignDefinition campaign, String node, long epoch) {
        return new StoryWorldData.PlayerSnapshot(
                PLAYER,
                campaign.id(),
                campaign.revision(),
                campaign.fingerprint(),
                epoch,
                node,
                campaign.completedPrefixFor(node),
                0,
                0);
    }
}
