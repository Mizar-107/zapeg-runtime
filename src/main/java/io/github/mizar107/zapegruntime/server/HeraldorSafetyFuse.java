package io.github.mizar107.zapegruntime.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent world-local mirror of the complete safety authority.
 *
 * <p>Vanilla deliberately swallows {@link IOException}s while saving {@code SavedData}. Heraldor
 * is therefore authorized only when this independently written, forced, atomically installed
 * record is healthy and exactly matches the in-memory/SavedData authority. Missing, unreadable,
 * corrupt, or mismatched records always quarantine.</p>
 */
final class HeraldorSafetyFuse {

    static final String FILE_NAME = "zapeg_runtime_heraldor_safety.authority";
    private static final int MAGIC = 0x5A485341; // ZHSA
    private static final int SCHEMA = 1;
    private static final int ENCODED_BYTES = Integer.BYTES * 5 + Long.BYTES * 7;

    private final Path path;
    private final Storage storage;

    HeraldorSafetyFuse(Path worldRoot) {
        this(worldRoot, new NioStorage());
    }

    HeraldorSafetyFuse(Path worldRoot, Storage storage) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        Path normalizedRoot = worldRoot.toAbsolutePath().normalize();
        this.path = normalizedRoot.resolve("data").resolve(FILE_NAME).normalize();
        if (!this.path.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("safety authority escaped the world root");
        }
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    Inspection inspect() {
        try {
            Optional<byte[]> encoded = storage.read(path);
            if (encoded.isEmpty()) {
                return Inspection.unsafe("missing");
            }
            return decode(encoded.get());
        } catch (IOException | RuntimeException unreadable) {
            return Inspection.unsafe("unreadable:" + unreadable.getClass().getSimpleName());
        }
    }

    Inspection install(HeraldorSafetyData expected) throws IOException {
        Objects.requireNonNull(expected, "expected");
        Inspection installed = install(expected.authoritySnapshot());
        if (!installed.matches(expected)) {
            throw new IOException("safety authority mismatch after install");
        }
        return installed;
    }

    Inspection install(HeraldorSafetyData.AuthoritySnapshot authority) throws IOException {
        Objects.requireNonNull(authority, "authority");
        byte[] encoded = encode(authority);
        storage.replaceAtomically(path, encoded);
        Optional<byte[]> actual = storage.read(path);
        if (actual.isEmpty() || !Arrays.equals(encoded, actual.get())) {
            throw new IOException("safety authority read-back mismatch");
        }
        Inspection inspected = decode(actual.get());
        if (!inspected.healthy()
                || inspected.authority() == null
                || !inspected.authority().matches(authority)) {
            throw new IOException("safety authority mismatch after install");
        }
        return inspected;
    }

    Path path() {
        return path;
    }

    private static byte[] encode(HeraldorSafetyData.AuthoritySnapshot authority) {
        boolean hasReplay = authority.lastNonce() != null && authority.lastRequest() != null;
        UUID lastNonce = hasReplay ? authority.lastNonce() : new UUID(0L, 0L);
        int lastRequest = hasReplay ? authority.lastRequest().ordinal() : -1;
        return ByteBuffer.allocate(ENCODED_BYTES)
                .putInt(MAGIC)
                .putInt(SCHEMA)
                .putInt(authority.configuredMode().ordinal())
                .putLong(authority.generation())
                .putLong(authority.nonce().getMostSignificantBits())
                .putLong(authority.nonce().getLeastSignificantBits())
                .putLong(authority.incidentId().getMostSignificantBits())
                .putLong(authority.incidentId().getLeastSignificantBits())
                .putInt(hasReplay ? 1 : 0)
                .putLong(lastNonce.getMostSignificantBits())
                .putLong(lastNonce.getLeastSignificantBits())
                .putInt(lastRequest)
                .array();
    }

    private static Inspection decode(byte[] encoded) {
        if (encoded == null || encoded.length != ENCODED_BYTES) {
            return Inspection.unsafe("invalid_length");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded);
        if (input.getInt() != MAGIC || input.getInt() != SCHEMA) {
            return Inspection.unsafe("invalid_header");
        }
        HeraldorSafetyMode[] modes = HeraldorSafetyMode.values();
        int modeOrdinal = input.getInt();
        long generation = input.getLong();
        UUID nonce = new UUID(input.getLong(), input.getLong());
        UUID incidentId = new UUID(input.getLong(), input.getLong());
        int hasReplay = input.getInt();
        UUID lastNonce = new UUID(input.getLong(), input.getLong());
        int lastRequestOrdinal = input.getInt();
        if (modeOrdinal < 0
                || modeOrdinal >= modes.length
                || generation < 0L
                || isNil(nonce)
                || isNil(incidentId)
                || (hasReplay != 0 && hasReplay != 1)) {
            return Inspection.unsafe("invalid_authority");
        }
        HeraldorSafetyMode lastRequest = null;
        if (hasReplay == 0) {
            if (!isNil(lastNonce) || lastRequestOrdinal != -1) {
                return Inspection.unsafe("invalid_empty_replay");
            }
            lastNonce = null;
        } else {
            if (isNil(lastNonce)
                    || lastRequestOrdinal < 0
                    || lastRequestOrdinal >= modes.length) {
                return Inspection.unsafe("invalid_replay");
            }
            lastRequest = modes[lastRequestOrdinal];
        }
        Authority authority = new Authority(
                modes[modeOrdinal], generation, nonce, incidentId, lastNonce, lastRequest);
        return new Inspection(true, "ok", authority);
    }

    private static boolean isNil(UUID value) {
        return value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L;
    }

    record Inspection(boolean healthy, String detail, Authority authority) {

        Inspection {
            Objects.requireNonNull(detail, "detail");
        }

        static Inspection unsafe(String detail) {
            return new Inspection(false, detail, null);
        }

        boolean matches(HeraldorSafetyData data) {
            return healthy
                    && authority != null
                    && authority.matches(data.authoritySnapshot());
        }
    }

    record Authority(
            HeraldorSafetyMode configuredMode,
            long generation,
            UUID nonce,
            UUID incidentId,
            UUID lastNonce,
            HeraldorSafetyMode lastRequest) {

        Authority {
            Objects.requireNonNull(configuredMode, "configuredMode");
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(incidentId, "incidentId");
        }

        boolean matches(HeraldorSafetyData.AuthoritySnapshot other) {
            return other != null
                    && configuredMode == other.configuredMode()
                    && generation == other.generation()
                    && nonce.equals(other.nonce())
                    && incidentId.equals(other.incidentId())
                    && Objects.equals(lastNonce, other.lastNonce())
                    && lastRequest == other.lastRequest();
        }
    }

    interface Storage {
        Optional<byte[]> read(Path path) throws IOException;

        void replaceAtomically(Path path, byte[] content) throws IOException;
    }

    private static final class NioStorage implements Storage {

        @Override
        public Optional<byte[]> read(Path path) throws IOException {
            return Files.exists(path)
                    ? Optional.of(Files.readAllBytes(path))
                    : Optional.empty();
        }

        @Override
        public void replaceAtomically(Path path, byte[] content) throws IOException {
            Path parent = path.getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, FILE_NAME + '.', ".tmp");
            boolean installed = false;
            try {
                try (FileChannel channel = FileChannel.open(
                        temporary,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(content);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                // There is deliberately no non-atomic fallback. If this filesystem cannot
                // provide the required install primitive, authority remains fail-closed.
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                installed = true;
                forceDirectoryWhereSupported(parent);
            } finally {
                if (!installed) {
                    Files.deleteIfExists(temporary);
                }
            }
        }

        private static void forceDirectoryWhereSupported(Path directory) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            } catch (AccessDeniedException | UnsupportedOperationException unsupported) {
                // Windows commonly denies directory handles. The atomically installed file was
                // itself forced before the move; directory forcing remains best-effort there.
            } catch (IOException ignored) {
                // Same fail-closed authority is established by read-back. A future install will
                // retry directory forcing rather than replacing atomic move with an unsafe path.
            }
        }
    }
}
