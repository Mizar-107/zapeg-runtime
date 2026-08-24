package io.github.mizar107.zapegruntime.server;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Verifies the bytes vanilla actually wrote instead of trusting its swallowed I/O failures. */
final class HeraldorSafetyPersistence {

    private static final String SAVED_DATA_ENVELOPE = "data";

    private HeraldorSafetyPersistence() {}

    static void flushAndVerify(MinecraftServer server, HeraldorSafetyData expected) {
        Objects.requireNonNull(server, "server");
        Path dataFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(HeraldorSafetyData.DATA_NAME + ".dat");
        flushAndVerify(
                expected,
                () -> server.overworld().getDataStorage().save(),
                () -> NbtIo.readCompressed(dataFile.toFile()),
                () -> forceFile(dataFile));
    }

    static void flushAndVerify(
            HeraldorSafetyData expected,
            IoAction vanillaFlush,
            IoSupplier<CompoundTag> diskReader,
            IoAction force) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(vanillaFlush, "vanillaFlush");
        Objects.requireNonNull(diskReader, "diskReader");
        Objects.requireNonNull(force, "force");
        expected.setDirty();
        try {
            // This call may return normally after an IOException. Read-back below is authority.
            vanillaFlush.run();
            force.run();
            CompoundTag envelope = Objects.requireNonNull(diskReader.get(), "persisted envelope");
            if (!envelope.contains(SAVED_DATA_ENVELOPE, Tag.TAG_COMPOUND)) {
                throw new IOException("safety SavedData envelope is absent");
            }
            HeraldorSafetyData persisted =
                    HeraldorSafetyData.load(envelope.getCompound(SAVED_DATA_ENVELOPE));
            if (!expected.sameAuthorization(persisted)) {
                throw new IOException("safety SavedData read-back mismatch");
            }
        } catch (IOException | RuntimeException failure) {
            // Vanilla clears dirty even when its write fails. Restore it so later enforcement
            // continues retrying while the independent fuse keeps the world quarantined.
            expected.setDirty();
            throw new IllegalStateException("Heraldor safety authority was not persisted", failure);
        }
    }

    private static void forceFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("safety SavedData file is absent");
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }
}
