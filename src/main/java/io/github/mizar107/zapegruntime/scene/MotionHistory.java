package io.github.mizar107.zapegruntime.scene;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Fixed-size transform history used only for a target client's transient echo. */
public final class MotionHistory {

    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final float[] yaw;
    private final int delaySamples;
    private int writeIndex;
    private int size;

    public record Sample(Vec3 position, float yawDegrees) {}

    public MotionHistory(int capacity, int delaySamples) {
        if (capacity < 2) {
            throw new IllegalArgumentException("Motion history capacity must be at least 2");
        }
        if (delaySamples < 1 || delaySamples >= capacity) {
            throw new IllegalArgumentException(
                    "Motion history delay must be between 1 and capacity - 1");
        }
        this.x = new double[capacity];
        this.y = new double[capacity];
        this.z = new double[capacity];
        this.yaw = new float[capacity];
        this.delaySamples = delaySamples;
    }

    public void record(Vec3 position, float yawDegrees) {
        if (position == null
                || !Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Motion history samples must be finite");
        }
        x[writeIndex] = position.x;
        y[writeIndex] = position.y;
        z[writeIndex] = position.z;
        yaw[writeIndex] = yawDegrees;
        writeIndex = (writeIndex + 1) % x.length;
        size = Math.min(size + 1, x.length);
    }

    public Optional<Sample> delayedSample() {
        return sampleBack(delaySamples);
    }

    /** The sample recorded {@code samplesBack} records ago, when it exists. */
    public Optional<Sample> sampleBack(int samplesBack) {
        if (samplesBack < 0 || samplesBack >= size) {
            return Optional.empty();
        }
        int index = Math.floorMod(writeIndex - 1 - samplesBack, x.length);
        return Optional.of(new Sample(
                new Vec3(x[index], y[index], z[index]),
                yaw[index]));
    }

    public int size() {
        return size;
    }

    public void clear() {
        writeIndex = 0;
        size = 0;
        java.util.Arrays.fill(x, 0.0D);
        java.util.Arrays.fill(y, 0.0D);
        java.util.Arrays.fill(z, 0.0D);
        java.util.Arrays.fill(yaw, 0.0F);
    }
}
