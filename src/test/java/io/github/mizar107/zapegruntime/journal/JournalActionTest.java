package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class JournalActionTest {

    @Test
    void closedActionIdsRoundTripAndMapOnlyTheTwoJournalBarriers() {
        assertEquals(2, JournalAction.values().length);
        for (JournalAction action : JournalAction.values()) {
            assertEquals(action, JournalAction.fromWireId(action.wireId()));
            assertEquals(action, JournalAction.forOrdinal(action.entryOrdinal()).orElseThrow());
        }
        assertThrows(IllegalArgumentException.class, () -> JournalAction.fromWireId(255));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "palimpsest_01"),
                JournalAction.REVEAL_PALIMPSEST.subject());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "absence_ledger"),
                JournalAction.COUNT_ABSENCES.subject());
    }

    @Test
    void factUuidIsStablePerPlayerCampaignEpochAndSubject() {
        UUID player = UUID.fromString("05575801-d37c-30bf-ae2c-bb3a46d2be3e");
        ResourceLocation campaign = ResourceLocation.fromNamespaceAndPath(
                "zapeg_runtime", "heraldor");
        ResourceLocation subject = JournalAction.REVEAL_PALIMPSEST.subject();
        UUID first = JournalFactIdentity.derive(player, campaign, 7L, subject);
        assertEquals(first, JournalFactIdentity.derive(player, campaign, 7L, subject));
        assertEquals(8, first.version());
        assertEquals(2, first.variant());
        assertNotEquals(first, JournalFactIdentity.derive(player, campaign, 8L, subject));
        assertNotEquals(
                first,
                JournalFactIdentity.derive(
                        UUID.randomUUID(), campaign, 7L, subject));
        assertNotEquals(
                first,
                JournalFactIdentity.derive(
                        player, campaign, 7L, JournalAction.COUNT_ABSENCES.subject()));
        assertThrows(
                IllegalArgumentException.class,
                () -> JournalFactIdentity.derive(player, campaign, -1L, subject));
    }
}
