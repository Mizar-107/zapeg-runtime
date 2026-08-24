package io.github.mizar107.zapegruntime.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JournalResourceContractTest {

    private static final String PREFIX = "journal.zapeg_runtime.heraldor.";

    @Test
    void englishAndTurkishKeysHaveExactParityAndThirtyCompleteEntries() throws IOException {
        JsonObject english = resourceJson("/assets/zapeg_runtime/lang/en_us.json");
        JsonObject turkish = resourceJson("/assets/zapeg_runtime/lang/tr_tr.json");
        assertEquals(english.keySet(), turkish.keySet());
        assertEquals(90L, english.keySet().stream()
                .filter(key -> key.startsWith(PREFIX))
                .count());
        assertEquals(30L, countSuffix(english.keySet(), ".title"));
        assertEquals(30L, countSuffix(english.keySet(), ".body"));
        assertEquals(30L, countSuffix(english.keySet(), ".clue"));
        for (String key : english.keySet()) {
            assertTrue(!english.get(key).getAsString().isBlank(), "blank en_us key " + key);
            assertTrue(!turkish.get(key).getAsString().isBlank(), "blank tr_tr key " + key);
        }
    }

    @Test
    void everyAuthoritativeCampaignJournalKeyHasLocalizedTitleBodyAndClue()
            throws IOException {
        JsonObject english = resourceJson("/assets/zapeg_runtime/lang/en_us.json");
        JsonObject turkish = resourceJson("/assets/zapeg_runtime/lang/tr_tr.json");
        JsonObject campaign = resourceJson(
                "/data/zapeg_runtime/heraldor_story/heraldor.json");
        assertEquals(30, campaign.getAsJsonArray("nodes").size());
        campaign.getAsJsonArray("nodes").forEach(element -> {
            String base = element.getAsJsonObject().get("journal_key").getAsString();
            for (String suffix : new String[] {".title", ".body", ".clue"}) {
                assertTrue(english.has(base + suffix), "missing en_us " + base + suffix);
                assertTrue(turkish.has(base + suffix), "missing tr_tr " + base + suffix);
            }
        });
    }

    @Test
    void onboardingAndQuestCluesStateTheConcreteMechanicsInBothLanguages() throws IOException {
        JsonObject en = resourceJson("/assets/zapeg_runtime/lang/en_us.json");
        JsonObject tr = resourceJson("/assets/zapeg_runtime/lang/tr_tr.json");
        assertContains(en, "first_scratch", "After sunset", "Brush", "extinguished Campfire");
        assertContains(en, "tracks_against_rain", "rain", "6 blocks", "40 ticks", "Mud");
        assertContains(en, "ninth_bell", "same loaded Bell", "9 times", "30 seconds");
        assertContains(en, "drowned_road", "eyes submerged", "8 blocks", "60 ticks", "Gravel");
        assertContains(en, "house_that_leans", "At night", "closed wooden Door", "60 ticks");
        assertContains(en, "door_below_door", "Below Y=0", "out of water", "3 blocks", "Trapdoor");
        assertContains(en, "ninth_witness", "At night", "Spyglass", "40 ticks", "exactly 8 Armor Stands");
        assertContains(en, "name_refused", "unrenamed Name Tag", "Soul Campfire");
        assertContains(en, "binder_knot", "Lead", "Tripwire Hook");
        assertContains(en, "first_seal", "3 Blaze Powder", "red Candle");
        assertContains(en, "second_seal", "3 Prismarine Crystals", "blue Candle");
        assertContains(en, "third_seal", "1 Echo Shard", "black Candle");
        assertContains(tr, "first_scratch", "Gün battıktan sonra", "Fırça", "sönmüş");
        assertContains(tr, "ninth_witness", "Gece", "Dürbünü", "40 tik", "tam 8 Zırh Askısı");
    }

    @Test
    void modelAndOriginalPixelTextureShipAtTheRegisteredItemPath() throws IOException {
        JsonObject model = resourceJson("/assets/zapeg_runtime/models/item/heraldor_journal.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals(
                "zapeg_runtime:item/heraldor_journal",
                model.getAsJsonObject("textures").get("layer0").getAsString());
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/zapeg_runtime/textures/item/heraldor_journal.png")) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
    }

    private static long countSuffix(Set<String> keys, String suffix) {
        return keys.stream()
                .filter(key -> key.startsWith(PREFIX) && key.endsWith(suffix))
                .count();
    }

    private static void assertContains(
            JsonObject language, String node, String... fragments) {
        String clue = language.get(PREFIX + node + ".clue").getAsString();
        for (String fragment : fragments) {
            assertTrue(clue.contains(fragment), () -> node + " clue lacks " + fragment);
        }
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = JournalResourceContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
