package io.github.mizar107.zapegruntime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HeraldorSafetyRevocationTest {

    @Test
    void alreadyQuarantinedStopDoesNotRevokeItsIdempotentGeneration() {
        assertFalse(HeraldorSafetyController.shouldLatchRevocation(
                HeraldorSafetyMode.QUARANTINED));
        assertTrue(HeraldorSafetyController.shouldLatchRevocation(
                HeraldorSafetyMode.MANUAL));
        assertTrue(HeraldorSafetyController.shouldLatchRevocation(
                HeraldorSafetyMode.LIVE));
        assertTrue(HeraldorSafetyController.shouldLatchRevocation(
                HeraldorSafetyMode.AUTO));
    }

    @Test
    void failedArmedBarrierRevokesImmediateAndLaterAdmission() throws IOException {
        for (HeraldorSafetyMode armed : new HeraldorSafetyMode[] {
            HeraldorSafetyMode.MANUAL, HeraldorSafetyMode.LIVE, HeraldorSafetyMode.AUTO
        }) {
            FailingStorage storage = new FailingStorage();
            HeraldorSafetyFuse fuse = new HeraldorSafetyFuse(Path.of("world"), storage);
            HeraldorSafetyData data = new HeraldorSafetyData();
            data.transition(armed, data.nonce());
            fuse.install(data);

            assertEquals(
                    armed,
                    HeraldorSafetyController.authorizedMode(
                            armed, true, true, false, true));

            // The atomic replacement fails and the old armed disk authority remains intact.
            // The revocation latch is installed before this attempt in the controller.
            storage.failReplacement = true;
            assertThrows(
                    IOException.class,
                    () -> fuse.install(data.quarantineBarrierSnapshot()));
            assertTrue(fuse.inspect().matches(data));

            HeraldorSafetyMode immediate = HeraldorSafetyController.authorizedMode(
                    armed, true, true, true, true);
            assertEquals(HeraldorSafetyMode.QUARANTINED, immediate);
            assertFalse(immediate.allows(HeraldorSafetyMode.MANUAL));

            // Model the next enforce in the same JVM: the revoked old generation is demoted
            // rather than automatically certified and restored.
            assertTrue(HeraldorSafetyController.startupMustQuarantine(
                    false, armed, true));
            data.emergencyQuarantine();
            assertEquals(
                    HeraldorSafetyMode.QUARANTINED,
                    HeraldorSafetyController.authorizedMode(
                            data.configuredMode(), false, true, false, true));
        }
    }

    @Test
    void everyStopPersistenceCombinationIsFailClosedAcrossRestart() throws IOException {
        for (HeraldorSafetyMode armed : new HeraldorSafetyMode[] {
            HeraldorSafetyMode.MANUAL, HeraldorSafetyMode.LIVE, HeraldorSafetyMode.AUTO
        }) {
            for (int mask = 0; mask < 8; mask++) {
                boolean barrierPersisted = (mask & 1) != 0;
                boolean finalAuthorityPersisted = (mask & 2) != 0;
                boolean savedDataPersisted = (mask & 4) != 0;
                boolean anyDurableWrite = mask != 0;

                assertEquals(
                        anyDurableWrite,
                        HeraldorSafetyController.durableRevocationProven(
                                barrierPersisted,
                                finalAuthorityPersisted,
                                savedDataPersisted));

                HeraldorSafetyData armedData = new HeraldorSafetyData();
                armedData.transition(armed, armedData.nonce());
                HeraldorSafetyData.AuthoritySnapshot armedSnapshot =
                        armedData.authoritySnapshot();
                HeraldorSafetyData.AuthoritySnapshot barrierSnapshot =
                        armedData.quarantineBarrierSnapshot();
                HeraldorSafetyData stoppedData = copyOf(armedData);
                stoppedData.emergencyQuarantine();
                HeraldorSafetyData.AuthoritySnapshot stoppedSnapshot =
                        stoppedData.authoritySnapshot();

                // Apply exactly the successful authority writes to real fuse encode/decode
                // storage. Final authority supersedes the earlier transition barrier.
                FailingStorage storage = new FailingStorage();
                HeraldorSafetyFuse fuse = new HeraldorSafetyFuse(Path.of("world"), storage);
                fuse.install(armedData);
                if (barrierPersisted) {
                    fuse.install(barrierSnapshot);
                }
                if (finalAuthorityPersisted) {
                    fuse.install(stoppedData);
                }
                HeraldorSafetyData persistedData = savedDataPersisted
                        ? copyOf(stoppedData)
                        : copyOf(armedData);
                assertEquals(
                        savedDataPersisted ? stoppedSnapshot : armedSnapshot,
                        persistedData.authoritySnapshot());
                HeraldorSafetyData.AuthoritySnapshot expectedDiskAuthority =
                        finalAuthorityPersisted
                                ? stoppedSnapshot
                                : barrierPersisted ? barrierSnapshot : armedSnapshot;
                assertTrue(fuse.inspect().authority().matches(expectedDiskAuthority));

                // Immediate process state is quarantined even if every write failed.
                HeraldorSafetyMode immediate = HeraldorSafetyController.authorizedMode(
                        stoppedData.configuredMode(),
                        fuse.inspect().matches(stoppedData),
                        true,
                        false,
                        true);
                assertEquals(HeraldorSafetyMode.QUARANTINED, immediate);
                assertFalse(immediate.allows(HeraldorSafetyMode.MANUAL));

                // Reconstruct the complete SavedData authority. This assertion distinguishes
                // exact old-pair survival from same-mode generation/nonce/replay mismatches.
                HeraldorSafetyData restartedData = copyOf(persistedData);
                boolean exactBeforeBoot = fuse.inspect().matches(restartedData);
                assertEquals(
                        mask == 0 || (finalAuthorityPersisted && savedDataPersisted),
                        exactBeforeBoot);
                boolean bootDemotion = HeraldorSafetyController.startupMustQuarantine(
                        true, restartedData.configuredMode(), false);
                if (bootDemotion) {
                    restartedData.emergencyQuarantine();
                }
                HeraldorSafetyMode restarted = HeraldorSafetyController.authorizedMode(
                        restartedData.configuredMode(),
                        fuse.inspect().matches(restartedData),
                        true,
                        false,
                        true);
                assertEquals(HeraldorSafetyMode.QUARANTINED, restarted);
                assertFalse(restarted.allows(HeraldorSafetyMode.MANUAL));
            }
        }
    }

    @Test
    void unprovedStopIsAnExplicitCommandFailure() {
        HeraldorSafetyController.StopOutcome failed = new HeraldorSafetyController.StopOutcome(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new HeraldorSafetyController.CleanupCounts(0, 0, 0, 0, 3),
                false);
        assertFalse(failed.success());
        assertTrue(failed.machineLine().startsWith(
                "heraldor_safety stop_failed reason=persistence_failed mode=quarantined"));

        HeraldorSafetyController.StopOutcome unresolved = new HeraldorSafetyController.StopOutcome(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new HeraldorSafetyController.CleanupCounts(0, 0, 0, 0, 1),
                true);
        assertFalse(unresolved.success());
        assertTrue(unresolved.machineLine().startsWith(
                "heraldor_safety stop_failed reason=cleanup_unresolved mode=quarantined"));

        HeraldorSafetyController.StopOutcome proved = new HeraldorSafetyController.StopOutcome(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new HeraldorSafetyController.CleanupCounts(0, 0, 0, 0, 0),
                true);
        assertTrue(proved.success());
        assertTrue(proved.machineLine().startsWith(
                "heraldor_safety stopped mode=quarantined"));
    }

    private static HeraldorSafetyData copyOf(HeraldorSafetyData source) {
        return HeraldorSafetyData.load(source.save(new CompoundTag()));
    }

    private static final class FailingStorage implements HeraldorSafetyFuse.Storage {
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
