package io.github.mizar107.zapegruntime.boss.presentation;

import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Locale;
import java.util.Objects;

/** Pure client policy for restrained phase, telegraph, and emissive presentation. */
public final class NinthFormRenderState {

    public static final float MAX_EMISSIVE_ALPHA = 0.68F;

    private NinthFormRenderState() {}

    public static VisualState resolve(
            NinthFormPhase phase,
            String attackId,
            int attackTick,
            int brokenPointMask,
            float ageInTicks) {
        Objects.requireNonNull(phase, "phase");
        AttackTiming timing = AttackTiming.parse(attackId);
        AttackWindow window = timing.windowAt(Math.max(0, attackTick));
        float progress = timing.windupProgress(Math.max(0, attackTick));
        boolean telegraphing = window == AttackWindow.WINDUP;

        float baseline = switch (phase) {
            case PRELUDE -> 0.05F;
            case FIRST -> 0.15F;
            case INTERLUDE -> 0.24F;
            case FINAL -> 0.28F;
            case BANISHED -> 0.0F;
        };
        float windowLift = switch (window) {
            case IDLE, COMPLETE -> 0.0F;
            case WINDUP -> 0.30F * smoothstep(progress);
            case ACTIVE -> 0.34F;
            case RECOVERY -> 0.08F;
        };
        float pulse = 0.88F + 0.12F * (float) Math.sin(ageInTicks * 0.29F);
        float emissiveAlpha = clamp((baseline + windowLift) * pulse, 0.0F, MAX_EMISSIVE_ALPHA);
        if (phase == NinthFormPhase.BANISHED) {
            emissiveAlpha = 0.0F;
        }

        float rollDegrees = phase == NinthFormPhase.INTERLUDE
                ? 1.10F * (float) Math.sin(ageInTicks * 0.17F)
                : telegraphing
                        ? 0.32F * (float) Math.sin(ageInTicks * 0.47F)
                        : 0.0F;
        boolean keelExposed = phase == NinthFormPhase.FINAL
                || timing == AttackTiming.KEEL_SWEEP
                || timing == AttackTiming.WAKE_CHARGE;
        RenderMode renderMode = switch (phase) {
            case PRELUDE, INTERLUDE, BANISHED -> RenderMode.TRANSLUCENT;
            case FIRST, FINAL -> RenderMode.CUTOUT;
        };
        return new VisualState(
                phase,
                timing,
                window,
                progress,
                emissiveAlpha,
                rollDegrees,
                renderMode,
                telegraphing,
                keelExposed,
                (brokenPointMask & 0b001) == 0,
                (brokenPointMask & 0b010) == 0,
                (brokenPointMask & 0b100) == 0);
    }

    private static float smoothstep(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum RenderMode {
        CUTOUT,
        TRANSLUCENT
    }

    public enum AttackWindow {
        IDLE,
        WINDUP,
        ACTIVE,
        RECOVERY,
        COMPLETE
    }

    public enum AttackTiming {
        IDLE("idle", 0, 0, 0),
        KEEL_SWEEP("keel_sweep", 30, 10, 26),
        ANCHORFALL("anchorfall", 36, 8, 30),
        UNDERTOW("undertow", 28, 20, 28),
        DROWNED_BROADSIDE("drowned_broadside", 42, 8, 30),
        WAKE_CHARGE("wake_charge", 32, 16, 30),
        NINEFOLD_GAZE("ninefold_gaze", 48, 18, 34);

        private final String id;
        private final int windupTicks;
        private final int activeTicks;
        private final int recoveryTicks;

        AttackTiming(String id, int windupTicks, int activeTicks, int recoveryTicks) {
            this.id = id;
            this.windupTicks = windupTicks;
            this.activeTicks = activeTicks;
            this.recoveryTicks = recoveryTicks;
        }

        public String id() {
            return id;
        }

        public int windupTicks() {
            return windupTicks;
        }

        public int activeTicks() {
            return activeTicks;
        }

        public int recoveryTicks() {
            return recoveryTicks;
        }

        public int totalTicks() {
            return windupTicks + activeTicks + recoveryTicks;
        }

        public AttackWindow windowAt(int tick) {
            if (this == IDLE) {
                return AttackWindow.IDLE;
            }
            if (tick < windupTicks) {
                return AttackWindow.WINDUP;
            }
            if (tick < windupTicks + activeTicks) {
                return AttackWindow.ACTIVE;
            }
            if (tick < totalTicks()) {
                return AttackWindow.RECOVERY;
            }
            return AttackWindow.COMPLETE;
        }

        public float windupProgress(int tick) {
            if (this == IDLE || windupTicks == 0) {
                return 0.0F;
            }
            return clamp((tick + 1.0F) / windupTicks, 0.0F, 1.0F);
        }

        public static AttackTiming parse(String value) {
            if (value == null) {
                return IDLE;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            for (AttackTiming timing : values()) {
                if (timing.id.equals(normalized)) {
                    return timing;
                }
            }
            return IDLE;
        }
    }

    public record VisualState(
            NinthFormPhase phase,
            AttackTiming attack,
            AttackWindow window,
            float windupProgress,
            float emissiveAlpha,
            float rollDegrees,
            RenderMode renderMode,
            boolean telegraphing,
            boolean keelExposed,
            boolean prowAlive,
            boolean portAlive,
            boolean starboardAlive) {

        public VisualState {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(attack, "attack");
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(renderMode, "renderMode");
            if (windupProgress < 0.0F
                    || windupProgress > 1.0F
                    || emissiveAlpha < 0.0F
                    || emissiveAlpha > MAX_EMISSIVE_ALPHA) {
                throw new IllegalArgumentException("unbounded Ninth Form visual state");
            }
        }
    }
}
