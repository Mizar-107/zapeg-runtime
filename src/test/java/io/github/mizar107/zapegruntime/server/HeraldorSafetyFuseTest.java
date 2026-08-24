package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeraldorSafetyFuseTest {

    @TempDir
    Path temporaryWorld;

    @Test
    void missingCorruptAndMismatchedAuthorityAreFailClosed() throws IOException {
        MemoryStorage storage = new MemoryStorage();
        HeraldorSafetyFuse fuse = new HeraldorSafetyFuse(Path.of("world"), storage);
        HeraldorSafetyData data = new HeraldorSafetyData();

        assertFalse(fuse.inspect().healthy());
        assertEquals("missing", fuse.inspect().detail());
        storage.content = new byte[] {1, 2, 3};
        assertFalse(fuse.inspect().healthy());

        HeraldorSafetyFuse.Inspection installed = fuse.install(data);
        assertTrue(installed.healthy());
        assertTrue(installed.matches(data));
        data.transition(HeraldorSafetyMode.MANUAL, data.nonce());
        assertFalse(fuse.inspect().matches(data));
    }

    @Test
    void failedAtomicReplacementRetainsTheOldUnsafeLatch() throws IOException {
        MemoryStorage storage = new MemoryStorage();
        HeraldorSafetyFuse fuse = new HeraldorSafetyFuse(Path.of("world"), storage);
        HeraldorSafetyData data = new HeraldorSafetyData();
        HeraldorSafetyData.AuthoritySnapshot quarantined = data.authoritySnapshot();
        fuse.install(data);

        data.transition(HeraldorSafetyMode.AUTO, data.nonce());
        storage.failReplacement = true;
        assertThrows(IOException.class, () -> fuse.install(data));

        HeraldorSafetyFuse.Inspection retained = fuse.inspect();
        assertTrue(retained.healthy());
        assertEquals(quarantined.configuredMode(), retained.authority().configuredMode());
        assertFalse(retained.matches(data));
    }

    @Test
    void realFilesystemInstallAndAtomicReplacementReadBackExactly() throws IOException {
        HeraldorSafetyFuse fuse = new HeraldorSafetyFuse(temporaryWorld);
        HeraldorSafetyData data = new HeraldorSafetyData();
        assertTrue(fuse.install(data).matches(data));

        data.transition(HeraldorSafetyMode.MANUAL, data.nonce());
        assertTrue(fuse.install(data).matches(data));
        assertTrue(fuse.inspect().matches(data));
    }

    private static final class MemoryStorage implements HeraldorSafetyFuse.Storage {
        private byte[] content;
        private boolean failReplacement;

        @Override
        public Optional<byte[]> read(Path path) {
            return content == null
                    ? Optional.empty()
                    : Optional.of(Arrays.copyOf(content, content.length));
        }

        @Override
        public void replaceAtomically(Path path, byte[] next) throws IOException {
            if (failReplacement) {
                throw new IOException("injected atomic replacement failure");
            }
            content = Arrays.copyOf(next, next.length);
        }
    }
}
