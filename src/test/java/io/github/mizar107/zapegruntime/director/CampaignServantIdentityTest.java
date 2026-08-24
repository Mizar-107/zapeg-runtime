package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mizar107.zapegruntime.servant.ServantArchetype;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CampaignServantIdentityTest {

    private static final UUID PLAYER = UUID.fromString("59a8dc4e-0af7-4c6a-b5e2-488d2bef64a0");
    private static final ResourceLocation CAMPAIGN = id("heraldor");
    private static final ResourceLocation SUBJECT = id("stalker_01");
    private static final String FINGERPRINT = "1".repeat(64);

    @Test
    void exactCampaignEnvelopeDerivesOneStableNonPlayerUuid() {
        UUID first = derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER);
        UUID second = derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER);

        assertEquals(first, second);
        assertNotEquals(new UUID(0L, 0L), first);
        assertNotEquals(PLAYER, first);
        assertEquals(8, first.version());
        assertEquals(2, first.variant());
    }

    @Test
    void everyRecoveryAndDefinitionFieldIsPartOfTheIdentity() {
        UUID baseline = derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER);

        assertNotEquals(baseline, derive(UUID.randomUUID(), CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, id("other"), 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 2, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 1, "2".repeat(64), 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 5L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "other_node", SUBJECT, ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", id("herald_01"), ServantArchetype.STALKER));
        assertNotEquals(baseline, derive(PLAYER, CAMPAIGN, 1, FINGERPRINT, 4L,
                "servant_of_distance", SUBJECT, ServantArchetype.HERALD));
    }

    @Test
    void invalidOrNonServantEnvelopesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> derive(
                new UUID(0L, 0L), CAMPAIGN, 1, FINGERPRINT, 0L,
                "servant_of_distance", SUBJECT, ServantArchetype.STALKER));
        assertThrows(IllegalArgumentException.class, () -> CampaignServantIdentity.derive(
                PLAYER,
                CAMPAIGN,
                1,
                FINGERPRINT,
                0L,
                "servant_of_distance",
                StoryFactType.SCENE_COMPLETED,
                SUBJECT,
                ServantArchetype.STALKER));
    }

    private static UUID derive(
            UUID player,
            ResourceLocation campaign,
            int revision,
            String fingerprint,
            long epoch,
            String node,
            ResourceLocation subject,
            ServantArchetype archetype) {
        return CampaignServantIdentity.derive(
                player,
                campaign,
                revision,
                fingerprint,
                epoch,
                node,
                StoryFactType.SERVANT_DEFEATED,
                subject,
                archetype);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }
}
