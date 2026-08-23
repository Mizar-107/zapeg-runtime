package io.github.mizar107.zapegruntime.scene;

/**
 * Pure, target-local choreography for {@code breach_01} and the visitation
 * fallback. All timing is normalized to the delivered body TTL, so Director
 * TTL scaling changes the pace without deleting a beat.
 *
 * <p>The presentation intentionally has no world-space inputs. It cannot be
 * occluded, culled, shader-replaced or stranded on an unloaded ground anchor.
 */
public final class BreachChoreography {

    /** Full-screen darkness stays translucent; it never hard-cuts to black. */
    public static final double MAX_VEIL_OPACITY = 0.72D;
    /** The manifestation is dark-on-dark rather than a bright flash. */
    public static final double MAX_MANIFESTATION_OPACITY = 0.84D;
    /** Eye luminance and alpha are both deliberately muted. */
    public static final double MAX_EYE_OPACITY = 0.68D;

    public enum SoundKind {
        KNOCK,
        FOOTSTEP,
        WHISPER,
        MANIFESTATION
    }

    /** Ordered, one-shot beats; the fractional offsets remain far apart. */
    public enum Cue {
        FIRST_KNOCK(SoundKind.KNOCK, 0.07D, -1),
        ANSWERING_KNOCK(SoundKind.KNOCK, 0.17D, -1),
        FIRST_STEP(SoundKind.FOOTSTEP, 0.30D, 0),
        SECOND_STEP(SoundKind.FOOTSTEP, 0.39D, 1),
        THIRD_STEP(SoundKind.FOOTSTEP, 0.48D, 2),
        WHISPER(SoundKind.WHISPER, 0.56D, -1),
        MANIFESTATION(SoundKind.MANIFESTATION, 0.64D, -1),
        FINAL_STEP(SoundKind.FOOTSTEP, 0.79D, 3);

        private final SoundKind soundKind;
        private final double fraction;
        private final int footstepIndex;

        Cue(SoundKind soundKind, double fraction, int footstepIndex) {
            this.soundKind = soundKind;
            this.fraction = fraction;
            this.footstepIndex = footstepIndex;
        }

        public SoundKind soundKind() {
            return soundKind;
        }

        public double fraction() {
            return fraction;
        }

        public int footstepIndex() {
            return footstepIndex;
        }
    }

    public record Frame(
            double veilOpacity,
            double doorwayClosure,
            double seamOpacity,
            double manifestationOpacity,
            double eyeOpacity,
            double horizontalDrift) {}

    private BreachChoreography() {}

    /** One cue maps to one bounded tick inside the body. */
    public static int cueTick(Cue cue, int bodyTicks) {
        int boundedTicks = Math.max(1, bodyTicks);
        if (boundedTicks == 1) {
            return 0;
        }
        return Math.max(
                1,
                Math.min(boundedTicks - 1, (int) Math.round(cue.fraction * boundedTicks)));
    }

    /**
     * Target-relative footstep offset, never more than five blocks away.
     * Callers rotate this pair by the target's current yaw; no wire anchor is
     * consulted.
     */
    public static double[] footstepOffset(long seed, int footstepIndex) {
        int index = Math.max(0, Math.min(3, footstepIndex));
        double direction = ((seed & 1L) == 0L ? 1.0D : -1.0D);
        double baseAngle = direction * (2.35D - index * 0.82D);
        double seededNudge = (Math.floorMod(seed >>> 11, 17L) - 8.0D) * 0.012D;
        double distance = 4.8D - index * 0.72D;
        double angle = baseAngle + seededNudge;
        return new double[] {
            Math.sin(angle) * distance,
            Math.cos(angle) * distance
        };
    }

    /** Frame values are finite and clamped even for hostile inputs. */
    public static Frame frame(double bodyAgeTicks, int bodyTicks, long seed) {
        int boundedTicks = Math.max(1, bodyTicks);
        double age = Double.isFinite(bodyAgeTicks) ? bodyAgeTicks : 0.0D;
        double progress = clamp(age / boundedTicks);

        double fadeIn = smoothstep(0.01D, 0.12D, progress);
        double fadeOut = 1.0D - smoothstep(0.88D, 1.0D, progress);
        double life = fadeIn * fadeOut;
        double veil = MAX_VEIL_OPACITY
                * life
                * (0.54D + 0.46D * smoothstep(0.20D, 0.68D, progress));

        double doorway = smoothstep(0.10D, 0.52D, progress)
                * (1.0D - smoothstep(0.82D, 0.98D, progress));
        // One slow breath over most of the body (~0.1 Hz at the default TTL),
        // not a blink or a high-contrast flash.
        double breath = 0.72D + 0.28D * Math.sin(Math.PI * clamp(progress / 0.92D));
        double seam = life * doorway * breath;

        double manifestation = MAX_MANIFESTATION_OPACITY
                * smoothstep(0.53D, 0.66D, progress)
                * (1.0D - smoothstep(0.80D, 0.94D, progress));
        double eyes = MAX_EYE_OPACITY
                * smoothstep(0.61D, 0.70D, progress)
                * (1.0D - smoothstep(0.76D, 0.88D, progress));

        double phase = Math.floorMod(seed, 997L) / 997.0D * Math.PI * 2.0D;
        double drift = Math.sin(phase + progress * Math.PI * 1.4D) * doorway;
        return new Frame(
                clamp(veil),
                clamp(doorway),
                clamp(seam),
                clamp(manifestation),
                clamp(eyes),
                Math.max(-1.0D, Math.min(1.0D, drift)));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (!(edge1 > edge0)) {
            return value >= edge1 ? 1.0D : 0.0D;
        }
        double unit = clamp((value - edge0) / (edge1 - edge0));
        return unit * unit * (3.0D - 2.0D * unit);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
