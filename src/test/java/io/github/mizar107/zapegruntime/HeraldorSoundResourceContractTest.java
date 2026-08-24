package io.github.mizar107.zapegruntime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HeraldorSoundResourceContractTest {

    private record Asset(int bytes, String sha256, double minSeconds, double maxSeconds) {}

    private static final Map<String, Asset> ASSETS = new LinkedHashMap<>();

    static {
        ASSETS.put("footstep_01.ogg", new Asset(
                4430, "75e311233d581bacd929ca3b39df3b97bd718491cfa06dfdf4115816801e951e",
                0.47D, 0.49D));
        ASSETS.put("footstep_02.ogg", new Asset(
                4505, "4aa7d1083eafd5ea00a3593984954bc2eecbb257b384bae26b511538298f4d6f",
                0.53D, 0.55D));
        ASSETS.put("knock_01.ogg", new Asset(
                4479, "1d14fbadf2f4e7aa1b45a6bcf2937f2c93207861cf6ee1b2f002b6a9f6497b2d",
                0.54D, 0.56D));
        ASSETS.put("knock_02.ogg", new Asset(
                4599, "dd9c056c58457289608a1ecdcc845fbfdd6117419de6cf805e9c09e3c7f1e768",
                0.61D, 0.63D));
        ASSETS.put("manifestation.ogg", new Asset(
                7645, "32e5d55ec7a7550d73633799241d04fb337fbde7d933cb2edbde86d64337d966",
                2.79D, 2.81D));
        ASSETS.put("whisper_01.ogg", new Asset(
                18680, "f6e27727abc8b5c1d9211b1f0cc2f2b4a388f3cfc2c9efb21efab6fdeea77010",
                1.79D, 1.81D));
        ASSETS.put("whisper_02.ogg", new Asset(
                20353, "2d0c09bc58b33688e6de92c197db1f3cd4af83f78dd03d10757e4bfe5c985d07",
                2.04D, 2.06D));
    }

    @Test
    void originalOggAssetsArePinnedUniqueMonoLengthFiles() throws Exception {
        Set<String> hashes = new HashSet<>();
        for (Map.Entry<String, Asset> entry : ASSETS.entrySet()) {
            byte[] bytes = resource("/assets/zapeg_runtime/sounds/heraldor/" + entry.getKey());
            Asset expected = entry.getValue();
            assertArrayEquals(new byte[] {'O', 'g', 'g', 'S'},
                    java.util.Arrays.copyOf(bytes, 4), entry.getKey());
            assertEquals(expected.bytes, bytes.length, entry.getKey());
            String hash = sha256(bytes);
            assertEquals(expected.sha256, hash, entry.getKey());
            assertTrue(hashes.add(hash), "audio variants must not be duplicate bytes");
            double seconds = finalGranule(bytes) / 44_100.0D;
            assertTrue(seconds >= expected.minSeconds && seconds <= expected.maxSeconds,
                    entry.getKey() + " duration=" + seconds);
        }
    }

    @Test
    void soundsJsonRegistersEveryOwnedFamilyWithSubtitlesAndBoundedGain()
            throws IOException {
        String sounds = text("/assets/zapeg_runtime/sounds.json");
        for (String event : new String[] {
            "heraldor_whisper_01", "heraldor_whisper_02",
            "heraldor_knock_01", "heraldor_knock_02",
            "heraldor_footstep_01", "heraldor_footstep_02",
            "heraldor_manifestation"
        }) {
            assertTrue(sounds.contains("\"" + event + "\""));
        }
        for (String subtitle : new String[] {
            "heraldor_whisper", "heraldor_knock", "heraldor_footstep",
            "heraldor_manifestation"
        }) {
            assertTrue(sounds.contains("subtitles.zapeg_runtime." + subtitle));
        }
        for (String asset : ASSETS.keySet()) {
            assertTrue(sounds.contains(asset.substring(0, asset.length() - 4)), asset);
        }
        Matcher volume = Pattern.compile("\\\"volume\\\"\\s*:\\s*([0-9.]+)").matcher(sounds);
        int count = 0;
        while (volume.find()) {
            double value = Double.parseDouble(volume.group(1));
            assertTrue(value > 0.0D && value <= 0.85D, "unbounded metadata volume");
            count++;
        }
        assertTrue(count >= ASSETS.size(), "all original Heraldor assets need bounded metadata");
        assertTrue(!sounds.contains("minecraft:"), "owned events cannot alias vanilla audio");
        assertTrue(!sounds.contains("http:"));
        assertTrue(!sounds.contains("https:"));
    }

    @Test
    void englishAndTurkishSubtitlesCoverEveryRegisteredEvent() throws IOException {
        String english = text("/assets/zapeg_runtime/lang/en_us.json");
        String turkish = text("/assets/zapeg_runtime/lang/tr_tr.json");
        for (String event : new String[] {
            "heraldor_whisper", "heraldor_knock", "heraldor_footstep",
            "heraldor_manifestation"
        }) {
            String key = "subtitles.zapeg_runtime." + event;
            assertTrue(english.contains(key), key);
            assertTrue(turkish.contains(key), key);
        }
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }

    private String text(String path) throws IOException {
        return new String(resource(path), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Reads the largest Ogg page granule; Vorbis assets are pinned at 44.1 kHz. */
    private static long finalGranule(byte[] bytes) {
        long largest = -1L;
        int offset = 0;
        while (offset + 27 <= bytes.length) {
            if (bytes[offset] != 'O' || bytes[offset + 1] != 'g'
                    || bytes[offset + 2] != 'g' || bytes[offset + 3] != 'S') {
                throw new AssertionError("invalid Ogg page at " + offset);
            }
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
