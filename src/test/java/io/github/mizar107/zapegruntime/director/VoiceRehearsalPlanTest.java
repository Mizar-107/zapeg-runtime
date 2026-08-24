package io.github.mizar107.zapegruntime.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class VoiceRehearsalPlanTest {

    @Test
    void packagedVoiceBindingsResolveToTheirExactAuthoredPresentations() throws Exception {
        DirectorSceneCatalog catalog = packagedCatalog();

        VoiceRehearsalPlan first = VoiceRehearsalPlan.resolve(
                        catalog, VoiceRehearsalPlan.VOICE_01)
                .orElseThrow();
        assertEquals(SceneProfile.BREACH_01, first.profile());
        assertEquals(140, first.ttlTicks());
        assertEquals(0, first.stage());
        assertEquals(1, first.presentationVariant());

        VoiceRehearsalPlan second = VoiceRehearsalPlan.resolve(
                        catalog, VoiceRehearsalPlan.VOICE_02)
                .orElseThrow();
        assertEquals(SceneProfile.BREACH_01, second.profile());
        assertEquals(220, second.ttlTicks());
        assertEquals(0, second.stage());
        assertEquals(6, second.presentationVariant());
    }

    @Test
    void unsupportedOrNonBreachBindingsFailClosed() {
        ResourceLocation unsupported = id("breach_01");
        DirectorSceneBinding wrongProfile = binding(
                StoryFactType.SCENE_COMPLETED,
                VoiceRehearsalPlan.VOICE_01,
                SceneProfile.RIFT_01);
        DirectorSceneBinding wrongProof = binding(
                StoryFactType.SCENE_PRESENTED,
                VoiceRehearsalPlan.VOICE_01,
                SceneProfile.BREACH_01);
        DirectorSceneBinding wrongSubject = binding(
                StoryFactType.SCENE_COMPLETED,
                VoiceRehearsalPlan.VOICE_02,
                SceneProfile.BREACH_01);

        assertTrue(VoiceRehearsalPlan.fromBinding(
                VoiceRehearsalPlan.VOICE_01, wrongProfile).isEmpty());
        assertTrue(VoiceRehearsalPlan.fromBinding(
                VoiceRehearsalPlan.VOICE_01, wrongProof).isEmpty());
        assertTrue(VoiceRehearsalPlan.fromBinding(
                VoiceRehearsalPlan.VOICE_01, wrongSubject).isEmpty());
        assertTrue(VoiceRehearsalPlan.fromBinding(unsupported, wrongProfile).isEmpty());
        assertEquals(
                java.util.List.of(
                        VoiceRehearsalPlan.VOICE_01,
                        VoiceRehearsalPlan.VOICE_02),
                VoiceRehearsalPlan.supportedSubjects());
    }

    @Test
    void rehearsalSeedUsesExactDirectorVariantEncodingWithoutIdentity() {
        UUID eventId = UUID.fromString("76dc9ecf-36c5-40ed-b09b-8d074871044f");
        VoiceRehearsalPlan first = new VoiceRehearsalPlan(
                VoiceRehearsalPlan.VOICE_01,
                SceneProfile.BREACH_01,
                140,
                0,
                1);
        VoiceRehearsalPlan second = new VoiceRehearsalPlan(
                VoiceRehearsalPlan.VOICE_02,
                SceneProfile.BREACH_01,
                220,
                0,
                6);

        assertEquals(
                DirectorSceneIdentity.presentationVariantByte(1),
                (int) (first.visualSeed(eventId) & 0xffL));
        assertEquals(
                DirectorSceneIdentity.presentationVariantByte(6),
                (int) (second.visualSeed(eventId) & 0xffL));
        assertEquals(first.visualSeed(eventId), first.visualSeed(eventId));
        assertNotEquals(first.visualSeed(eventId), second.visualSeed(eventId));
    }

    private static DirectorSceneBinding binding(
            StoryFactType factType,
            ResourceLocation subject,
            SceneProfile profile) {
        return new DirectorSceneBinding(
                factType,
                subject,
                profile,
                140,
                0,
                1,
                600,
                100);
    }

    private static DirectorSceneCatalog packagedCatalog() throws Exception {
        try (var stream = VoiceRehearsalPlanTest.class.getResourceAsStream(
                "/data/zapeg_runtime/heraldor_director/heraldor.json")) {
            if (stream == null) {
                throw new IllegalStateException("missing packaged Director catalog");
            }
            return DirectorSceneJsonParser.parse(
                    StoryCampaignRegistry.HERALDOR_CAMPAIGN,
                    JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", path);
    }
}
