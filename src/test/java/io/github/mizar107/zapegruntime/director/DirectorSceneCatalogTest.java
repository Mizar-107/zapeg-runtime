package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryCampaignDefinition;
import io.github.mizar107.zapegruntime.story.StoryCampaignJsonParser;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryNode;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DirectorSceneCatalogTest {

    private static final ResourceLocation CAMPAIGN = id("heraldor");

    @Test
    void packagedCatalogCoversEveryAndOnlyCampaignSceneTrigger() {
        DirectorSceneCatalog catalog = packagedCatalog();
        StoryCampaignDefinition campaign = packagedCampaign();
        Set<StoryTrigger> expected = new HashSet<>();
        for (StoryNode node : campaign.nodes()) {
            if (!node.terminal()
                    && (node.advanceOn().type() == StoryFactType.SCENE_COMPLETED
                            || node.advanceOn().type() == StoryFactType.SCENE_PRESENTED)) {
                expected.add(node.advanceOn());
            }
        }
        assertEquals(10, expected.size());
        assertEquals(expected, catalog.bindings().keySet());
    }

    @Test
    void tenSubjectMappingIsIntentionalAndPresentationCapable() {
        DirectorSceneCatalog catalog = packagedCatalog();
        Map<String, Expected> expected = Map.ofEntries(
                Map.entry("voice_01", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.BREACH_01, 140, 0, 1)),
                Map.entry("visitation_01", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.VISITATION_01, 170, 0, 2)),
                Map.entry("stalker_glimpse_01", new Expected(StoryFactType.SCENE_PRESENTED,
                        SceneProfile.PERIPHERAL_01, 140, 0, 3)),
                Map.entry("breach_01", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.BREACH_01, 180, 0, 4)),
                Map.entry("tide_omen_01", new Expected(StoryFactType.SCENE_PRESENTED,
                        SceneProfile.SKY_MARK_01, 240, 0, 5)),
                Map.entry("voice_02", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.BREACH_01, 220, 0, 6)),
                Map.entry("knock_sequence_01", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.BREACH_01, 120, 0, 7)),
                Map.entry("colossus_01", new Expected(StoryFactType.SCENE_PRESENTED,
                        SceneProfile.COLOSSUS_01, 320, 3, 8)),
                Map.entry("procession_01", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.NEAR_MISS_01, 110, 0, 9)),
                Map.entry("shipwreck_vision", new Expected(StoryFactType.SCENE_COMPLETED,
                        SceneProfile.RIFT_01, 200, 1, 10)));
        for (Map.Entry<String, Expected> entry : expected.entrySet()) {
            DirectorSceneBinding binding = catalog.find(new StoryTrigger(
                            entry.getValue().type(), id(entry.getKey())))
                    .orElseThrow();
            assertEquals(entry.getValue().profile(), binding.profile(), entry.getKey());
            assertEquals(entry.getValue().ttl(), binding.ttlTicks(), entry.getKey());
            assertEquals(entry.getValue().stage(), binding.stage(), entry.getKey());
            assertEquals(entry.getValue().variant(), binding.presentationVariant(), entry.getKey());
            if (binding.factType() == StoryFactType.SCENE_PRESENTED) {
                assertTrue(DirectorPresentationPolicy.visibleMeansPresented(binding.profile()),
                        entry.getKey());
            }
        }
    }

    @Test
    void sharedBreachProfileHasFourDistinctChoreographyAndAudioSeeds() {
        DirectorSceneCatalog catalog = packagedCatalog();
        List<String> subjects = List.of(
                "voice_01", "breach_01", "voice_02", "knock_sequence_01");
        Set<String> signatures = new HashSet<>();
        Set<Integer> variantBytes = new HashSet<>();
        Set<Integer> ttls = new HashSet<>();
        for (String subject : subjects) {
            DirectorSceneBinding binding = catalog.find(new StoryTrigger(
                            StoryFactType.SCENE_COMPLETED, id(subject)))
                    .orElseThrow();
            assertEquals(SceneProfile.BREACH_01, binding.profile());
            signatures.add(binding.presentationSignature());
            variantBytes.add(DirectorSceneIdentity.presentationVariantByte(
                    binding.presentationVariant()));
            ttls.add(binding.ttlTicks());
        }
        assertEquals(4, signatures.size());
        assertEquals(4, variantBytes.size());
        assertEquals(4, ttls.size());
    }

    @Test
    void duplicatePresentationSignatureRejectsWholeCatalog() {
        List<DirectorSceneBinding> bindings = new ArrayList<>();
        for (int index = 0; index < DirectorSceneCatalog.HERALDOR_BINDING_COUNT; index++) {
            bindings.add(new DirectorSceneBinding(
                    StoryFactType.SCENE_COMPLETED,
                    id("duplicate_" + index),
                    SceneProfile.BREACH_01,
                    180,
                    0,
                    1,
                    600,
                    100));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new DirectorSceneCatalog(CAMPAIGN, bindings));
    }

    @Test
    void variantChangesStableVisualSeedWithoutChangingEventIdentity() {
        DirectorSceneIdentity first = identity(1);
        DirectorSceneIdentity second = identity(4);
        assertNotEquals(first.visualSeed(), second.visualSeed());
        assertEquals(first.placementSeed(), second.placementSeed());
    }

    private static DirectorSceneCatalog packagedCatalog() {
        return DirectorSceneJsonParser.parse(CAMPAIGN, resource(
                "/data/zapeg_runtime/heraldor_director/heraldor.json"));
    }

    private static StoryCampaignDefinition packagedCampaign() {
        return StoryCampaignJsonParser.parse(CAMPAIGN, resource(
                "/data/zapeg_runtime/heraldor_story/heraldor.json"));
    }

    private static JsonElement resource(String path) {
        try (var stream = DirectorSceneCatalogTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static DirectorSceneIdentity identity(int variant) {
        return new DirectorSceneIdentity(
                UUIDS.EVENT,
                UUIDS.TARGET,
                CAMPAIGN,
                1,
                "a".repeat(64),
                0L,
                "voice_without_air",
                StoryFactType.SCENE_COMPLETED,
                id("voice_01"),
                "b".repeat(64),
                variant);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }

    private record Expected(
            StoryFactType type, SceneProfile profile, int ttl, int stage, int variant) {}

    private static final class UUIDS {
        private static final java.util.UUID EVENT =
                java.util.UUID.fromString("10000000-0000-4000-8000-000000000001");
        private static final java.util.UUID TARGET =
                java.util.UUID.fromString("20000000-0000-4000-8000-000000000002");
    }
}
