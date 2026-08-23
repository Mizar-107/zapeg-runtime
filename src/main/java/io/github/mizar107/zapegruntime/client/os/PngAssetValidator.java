package io.github.mizar107.zapegruntime.client.os;

import io.github.mizar107.zapegruntime.scene.OsEffectReason;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Small AWT-free PNG preflight safe to run before any toolkit is loaded. */
final class PngAssetValidator {

    private static final byte[] SIGNATURE = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final int MIN_DIMENSION = 64;
    private static final int MAX_DIMENSION = 4096;

    private PngAssetValidator() {}

    record Validation(boolean valid, OsEffectReason reason, int width, int height) {}

    static Validation validate(InputStream stream) {
        if (stream == null) {
            return invalid(OsEffectReason.ASSET_MISSING);
        }
        try {
            byte[] header = stream.readNBytes(24);
            if (header.length != 24
                    || !Arrays.equals(SIGNATURE, Arrays.copyOfRange(header, 0, 8))
                    || readInt(header, 8) != 13
                    || header[12] != 'I'
                    || header[13] != 'H'
                    || header[14] != 'D'
                    || header[15] != 'R') {
                return invalid(OsEffectReason.ASSET_INVALID);
            }
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            if (width < MIN_DIMENSION
                    || height < MIN_DIMENSION
                    || width > MAX_DIMENSION
                    || height > MAX_DIMENSION) {
                return invalid(OsEffectReason.ASSET_INVALID);
            }
            return new Validation(true, OsEffectReason.NONE, width, height);
        } catch (IOException invalid) {
            return invalid(OsEffectReason.ASSET_INVALID);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | bytes[offset + 3] & 0xff;
    }

    private static Validation invalid(OsEffectReason reason) {
        return new Validation(false, reason, 0, 0);
    }
}
