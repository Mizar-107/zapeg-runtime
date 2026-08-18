package io.github.mizar107.zapegruntime.scene;

/**
 * Bounded camera perturbation for scene unease. Pure math with hard caps so
 * the result is unit-testable and can never fight the player's control for
 * more than a moment: tekinsizlik is unease, never motion sickness.
 *
 * <p>Three layers combine into a {yaw, pitch, roll} degree offset:
 * a slow positional jitter, a brief decaying shake pulse after a reveal, and
 * an unnatural micro roll drift. Every layer is individually capped and the
 * sum is clamped again.
 */
public final class CameraUnease {

    public static final int MAX_LEVEL = 3;
    /** Hard caps in degrees, already at the strongest tier. */
    public static final float MAX_JITTER_YAW_DEGREES = 0.35F;
    public static final float MAX_JITTER_PITCH_DEGREES = 0.28F;
    public static final float MAX_ROLL_DEGREES = 0.80F;
    public static final float MAX_SHAKE_DEGREES = 1.00F;
    /** A reveal jolt always decays to nothing within this many ticks. */
    public static final int SHAKE_DECAY_TICKS = 14;
    /**
     * The colossus' ground reaction is deliberately stronger than any normal
     * scene shake — deep, slow pulses synced to its footfalls — but still
     * hard-capped: unease, never motion sickness, and never a held fight
     * against the player's control.
     */
    public static final float MAX_HEAVY_SHAKE_DEGREES = 2.50F;
    /** A footfall rumble settles slower than a reveal jolt: ground, not jump. */
    public static final int HEAVY_SHAKE_DECAY_TICKS = 24;
    /** Between footfalls the ground barely breathes. */
    public static final float HEAVY_SWAY_DEGREES = 0.18F;

    private CameraUnease() {}

    /**
     * @param uneaseLevel 0..{@link #MAX_LEVEL}
     * @param ageTicks scene body age including the partial tick
     * @param seed per-scene visual seed (decorrelates the noise)
     * @param intensity 0..1 scene envelope scaling
     * @param shakeTicks ticks since the reveal jolt; negative disables the shake
     * @return {yaw, pitch, roll} offsets in degrees, each within its hard cap
     */
    public static float[] perturbation(
            int uneaseLevel,
            double ageTicks,
            long seed,
            float intensity,
            int shakeTicks) {
        if (uneaseLevel <= 0 || intensity <= 0.0F || !Double.isFinite(ageTicks)) {
            return new float[3];
        }
        float tier = Math.min(uneaseLevel, MAX_LEVEL) / (float) MAX_LEVEL;
        float scale = tier * Math.min(1.0F, intensity);

        double phaseA = Math.floorMod(seed, 1024L) * 0.013D;
        double phaseB = Math.floorMod(seed >>> 11, 1024L) * 0.017D;
        double phaseC = Math.floorMod(seed >>> 23, 1024L) * 0.019D;

        // Two incommensurate sinusoids per axis read as organic drift, not a
        // mechanical oscillation.
        double jitterYaw = Math.sin(ageTicks * 1.71D + phaseA) * 0.62D
                + Math.sin(ageTicks * 3.97D + phaseB) * 0.38D;
        double jitterPitch = Math.sin(ageTicks * 1.37D + phaseB) * 0.60D
                + Math.sin(ageTicks * 3.13D + phaseC) * 0.40D;
        double roll = Math.sin(ageTicks * 0.211D + phaseC) * 0.75D
                + Math.sin(ageTicks * 0.083D + phaseA) * 0.25D;

        float yaw = (float) jitterYaw * MAX_JITTER_YAW_DEGREES * scale;
        float pitch = (float) jitterPitch * MAX_JITTER_PITCH_DEGREES * scale;
        float rollDegrees = (float) roll * MAX_ROLL_DEGREES * scale;

        if (shakeTicks >= 0 && shakeTicks < SHAKE_DECAY_TICKS) {
            float falloff = 1.0F - (float) shakeTicks / SHAKE_DECAY_TICKS;
            float amplitude = MAX_SHAKE_DEGREES * tier * falloff * falloff;
            double oscillation = Math.sin(shakeTicks * 2.1D + phaseA);
            yaw += (float) oscillation * amplitude;
            pitch += (float) Math.cos(shakeTicks * 1.7D + phaseB) * amplitude * 0.6F;
        }

        float yawCap = MAX_JITTER_YAW_DEGREES + MAX_SHAKE_DEGREES;
        float pitchCap = MAX_JITTER_PITCH_DEGREES + MAX_SHAKE_DEGREES;
        return new float[] {
            clamp(yaw, yawCap),
            clamp(pitch, pitchCap),
            clamp(rollDegrees, MAX_ROLL_DEGREES)
        };
    }

    /**
     * The colossus camera path: a barely-there ground sway plus one deep,
     * slow pulse per footfall. The pulse amplitude comes from the escalation
     * stage (bounded by {@link ColossusChoreography#shakeDegrees}) and always
     * decays to nothing within {@link #HEAVY_SHAKE_DECAY_TICKS}.
     *
     * @param stage escalation stage, clamped by the choreography
     * @param ageTicks scene body age including the partial tick
     * @param seed per-scene visual seed (decorrelates the noise)
     * @param intensity 0..1 scene envelope scaling
     * @param ticksSinceStep ticks since the last footfall; negative disables
     *     the pulse
     * @return {yaw, pitch, roll} offsets in degrees, each within its hard cap
     */
    public static float[] colossusPerturbation(
            int stage,
            double ageTicks,
            long seed,
            float intensity,
            int ticksSinceStep) {
        if (intensity <= 0.0F || !Double.isFinite(ageTicks)) {
            return new float[3];
        }
        float scale = Math.min(1.0F, intensity);
        double phaseA = Math.floorMod(seed, 1024L) * 0.013D;
        double phaseB = Math.floorMod(seed >>> 11, 1024L) * 0.017D;

        double swayYaw = Math.sin(ageTicks * 0.41D + phaseA) * 0.70D
                + Math.sin(ageTicks * 0.93D + phaseB) * 0.30D;
        double swayPitch = Math.sin(ageTicks * 0.37D + phaseB) * 0.65D
                + Math.sin(ageTicks * 0.77D + phaseA) * 0.35D;
        float yaw = (float) swayYaw * HEAVY_SWAY_DEGREES * scale;
        float pitch = (float) swayPitch * HEAVY_SWAY_DEGREES * scale;
        float roll = (float) Math.sin(ageTicks * 0.19D + phaseA)
                * MAX_ROLL_DEGREES * 0.5F * scale;

        if (ticksSinceStep >= 0 && ticksSinceStep < HEAVY_SHAKE_DECAY_TICKS) {
            float falloff = 1.0F - (float) ticksSinceStep / HEAVY_SHAKE_DECAY_TICKS;
            float amplitude = (float) ColossusChoreography.shakeDegrees(stage)
                    * falloff * falloff * scale;
            yaw += (float) Math.sin(ticksSinceStep * 1.05D + phaseA) * amplitude;
            pitch += (float) Math.cos(ticksSinceStep * 0.85D + phaseB) * amplitude * 0.7F;
            roll += (float) Math.sin(ticksSinceStep * 0.55D + phaseB) * amplitude * 0.25F;
        }

        float yawCap = HEAVY_SWAY_DEGREES + MAX_HEAVY_SHAKE_DEGREES;
        float pitchCap = HEAVY_SWAY_DEGREES + MAX_HEAVY_SHAKE_DEGREES * 0.7F;
        return new float[] {
            clamp(yaw, yawCap),
            clamp(pitch, pitchCap),
            clamp(roll, MAX_ROLL_DEGREES)
        };
    }

    private static float clamp(float value, float cap) {
        return Math.max(-cap, Math.min(cap, value));
    }
}
