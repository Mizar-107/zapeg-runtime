package io.github.mizar107.zapegruntime.boss.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class NinthFormPresentationResourceTest {

    private record Asset(int bytes, String sha256, double minSeconds, double maxSeconds) {}

    private static final Map<String, Asset> AUDIO = new LinkedHashMap<>();

    static {
        AUDIO.put("awakening.ogg", new Asset(
                6290,
                "a12f1687e6d9b5998c109223f2ca5151d31704c4d637b9180ac303a9a28192a6",
                1.84D,
                1.86D));
        AUDIO.put("banish.ogg", new Asset(
                6766,
                "873bed1b9bfd6195f1fbb68478c4cfc32b9ddd80c79f4aef93a446084595ec87",
                2.27D,
                2.28D));
        AUDIO.put("telegraph.ogg", new Asset(
                5092,
                "37904e6a7ae092afee17349885a3efae8c4d150055f924f012ec2786285a5481",
                0.94D,
                0.96D));
        AUDIO.put("weakpoint_break.ogg", new Asset(
                4995,
                "075d1d1ccec6b614cc8046ad70f75db4f9dafc93389670dd076ed7c653e4d20c",
                0.71D,
                0.73D));
        AUDIO.put("impact.ogg", new Asset(
                5002,
                "1d901f385d89b22bc28f7010cc22f9cebab90d3effd04d84863f30ea945164df",
                0.87D,
                0.89D));
        AUDIO.put("hurt.ogg", new Asset(
                4678,
                "3add0ae3e9c60475af2e482c6f8a453c7f8801b1eba674b52bd7686e948022b5",
                0.61D,
                0.63D));
        AUDIO.put("death.ogg", new Asset(
                6048,
                "aa93420b0ba0a561366c3f6f27ece930ac2114ab2b14027516f9ac3d913ce57a",
                1.64D,
                1.66D));
        AUDIO.put("bed.ogg", new Asset(
                12723,
                "c1501e818337c7527bba3f8c70ce9d55ed8650d9061c4745fe66e2fe2397c12d",
                6.39D,
                6.41D));
    }

    @Test
    void exactUvAtlasesArePinnedOriginalAndRestrained() throws Exception {
        verifyTexture(
                "ninth_form.png",
                18_563,
                "23ef98a86ef8b0fe6c9efa560506e14b66c66e1e48327288bf3b46f37c31dfa2",
                236_800,
                0);
        verifyTexture(
                "ninth_form_emissive.png",
                5_964,
                "6d9eee04073040cb903bde1712b2e17fd0ae699e9d8d5e1635e980412f549c8b",
                0,
                11_220);
    }

    @Test
    void proceduralOggAssetsArePinnedUniqueMonoLengthFiles() throws Exception {
        Set<String> hashes = new HashSet<>();
        for (Map.Entry<String, Asset> entry : AUDIO.entrySet()) {
            byte[] bytes = resource(
                    "/assets/zapeg_runtime/sounds/ninth_form/" + entry.getKey());
            Asset expected = entry.getValue();
            assertEquals(expected.bytes(), bytes.length, entry.getKey());
            assertEquals(expected.sha256(), sha256(bytes), entry.getKey());
            assertTrue(hashes.add(expected.sha256()), "audio assets must be byte-unique");
            assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
            double seconds = finalGranule(bytes) / 44_100.0D;
            assertTrue(seconds >= expected.minSeconds() && seconds <= expected.maxSeconds(),
                    entry.getKey() + " duration=" + seconds);
        }
    }

    @Test
    void soundDefinitionsAreOwnedLocalizedAndGainBounded() throws Exception {
        JsonObject sounds = json("/assets/zapeg_runtime/sounds.json");
        Map<String, String> events = Map.of(
                "ninth_form_awakening", "awakening",
                "ninth_form_telegraph", "telegraph",
                "ninth_form_weakpoint_break", "weakpoint_break",
                "ninth_form_banish", "banish",
                "ninth_form_impact", "impact",
                "ninth_form_hurt", "hurt",
                "ninth_form_death", "death",
                "ninth_form_bed", "bed");
        for (Map.Entry<String, String> entry : events.entrySet()) {
            JsonObject definition = sounds.getAsJsonObject(entry.getKey());
            assertNotNull(definition, entry.getKey());
            assertEquals(
                    "subtitles.zapeg_runtime.ninth_form." + entry.getValue(),
                    definition.get("subtitle").getAsString());
            JsonObject sound = definition.getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertEquals(
                    "zapeg_runtime:ninth_form/" + entry.getValue(),
                    sound.get("name").getAsString());
            assertTrue(sound.get("volume").getAsDouble() > 0.0D);
            assertTrue(sound.get("volume").getAsDouble() <= 0.78D);
        }
    }

    @Test
    void englishAndTurkishCoverBossPartsPhasesAttacksSoundsAndToast() throws Exception {
        JsonObject english = json("/assets/zapeg_runtime/lang/en_us.json");
        JsonObject turkish = json("/assets/zapeg_runtime/lang/tr_tr.json");
        Set<String> keys = new HashSet<>();
        keys.add("entity.zapeg_runtime.ninth_form");
        for (String part : new String[] {
            "prow_lantern", "port_mooring", "starboard_mooring",
            "keel_heart", "armored_hull_aft"
        }) {
            keys.add("entity.zapeg_runtime.ninth_form.part." + part);
        }
        for (String phase : new String[] {"prelude", "first", "interlude", "final", "banished"}) {
            keys.add("boss.zapeg_runtime.ninth_form.phase." + phase);
        }
        for (String attack : new String[] {
            "keel_sweep", "anchorfall", "undertow", "drowned_broadside",
            "wake_charge", "ninefold_gaze"
        }) {
            keys.add("attack.zapeg_runtime.ninth_form." + attack);
        }
        for (String sound : new String[] {
            "awakening", "telegraph", "weakpoint_break", "banish",
            "impact", "hurt", "death", "bed"
        }) {
            keys.add("subtitles.zapeg_runtime.ninth_form." + sound);
        }
        keys.add("advancements.zapeg_runtime.heraldor.banish_ninth_form.title");
        keys.add("advancements.zapeg_runtime.heraldor.banish_ninth_form.description");
        for (String key : keys) {
            assertTrue(english.has(key), "missing English " + key);
            assertTrue(turkish.has(key), "missing Turkish " + key);
            assertFalse(english.get(key).getAsString().isBlank(), key);
            assertFalse(turkish.get(key).getAsString().isBlank(), key);
        }
    }

    @Test
    void encounterOwnedToastUsesTranslationComponentsWhenMerged() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/data/zapeg_runtime/advancements/heraldor/banish_ninth_form.json")) {
            if (stream == null) {
                return; // The encounter slice owns this resource and is merged later.
            }
            JsonObject display = JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("display");
            assertEquals(
                    "advancements.zapeg_runtime.heraldor.banish_ninth_form.title",
                    display.getAsJsonObject("title").get("translate").getAsString());
            assertEquals(
                    "advancements.zapeg_runtime.heraldor.banish_ninth_form.description",
                    display.getAsJsonObject("description").get("translate").getAsString());
            assertTrue(display.get("show_toast").getAsBoolean());
            assertTrue(display.get("hidden").getAsBoolean());
            assertFalse(display.get("announce_to_chat").getAsBoolean());
        }
    }

    @Test
    void provenanceNamesEveryGeneratorAndPinnedAsset() throws Exception {
        Path docs = Path.of("docs", "NINTH-FORM-ASSETS.md");
        String provenance = Files.readString(docs);
        assertTrue(provenance.contains("Generate-NinthFormTextures.ps1"));
        assertTrue(provenance.contains("Generate-NinthFormAudio.ps1"));
        assertTrue(provenance.contains("Generate-HeraldorPresenceAssets.py"));
        assertTrue(provenance.contains("No Minecraft texture"));
        for (String asset : AUDIO.keySet()) {
            assertTrue(provenance.contains(asset), asset);
        }
        assertTrue(provenance.contains("ninth_form.png"));
        assertTrue(provenance.contains("ninth_form_emissive.png"));
        assertTrue(Files.isRegularFile(Path.of("tools", "Generate-NinthFormTextures.ps1")));
        assertTrue(Files.isRegularFile(Path.of("tools", "Generate-NinthFormAudio.ps1")));
    }

    private void verifyTexture(
            String name,
            int expectedBytes,
            String expectedHash,
            int expectedOpaque,
            int expectedPartial) throws Exception {
        byte[] bytes = resource("/assets/zapeg_runtime/textures/entity/" + name);
        assertEquals(expectedBytes, bytes.length, name);
        assertEquals(expectedHash, sha256(bytes), name);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, name);
        assertEquals(NinthFormUvLayout.WIDTH, image.getWidth());
        assertEquals(NinthFormUvLayout.HEIGHT, image.getHeight());
        int opaque = 0;
        int partial = 0;
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 255) {
                    opaque++;
                } else if (alpha != 0) {
                    partial++;
                }
                if (alpha != 0) {
                    colors.add(argb);
                }
            }
        }
        assertEquals(expectedOpaque, opaque, name);
        assertEquals(expectedPartial, partial, name);
        assertTrue(colors.size() >= 30, name + " needs procedural variation");
        if (name.contains("emissive")) {
            assertTrue(partial < image.getWidth() * image.getHeight() / 20,
                    "emissive coverage must remain below five percent");
        }
    }

    private JsonObject json(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static long finalGranule(byte[] bytes) {
        long largest = -1L;
        int offset = 0;
        while (offset + 27 <= bytes.length) {
            assertEquals('O', bytes[offset]);
            assertEquals('g', bytes[offset + 1]);
            assertEquals('g', bytes[offset + 2]);
            assertEquals('S', bytes[offset + 3]);
            long granule = ByteBuffer.wrap(bytes, offset + 6, 8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getLong();
            largest = Math.max(largest, granule);
            int segments = Byte.toUnsignedInt(bytes[offset + 26]);
            int body = 0;
            for (int index = 0; index < segments; index++) {
                body += Byte.toUnsignedInt(bytes[offset + 27 + index]);
            }
            offset += 27 + segments + body;
        }
        assertEquals(bytes.length, offset, "Ogg pages must consume the whole resource");
        assertTrue(largest > 0L);
        return largest;
    }
}
