package io.github.mizar107.zapegruntime.scene;

import java.util.Arrays;
import java.util.Locale;

public enum SceneProfile {
    // Local-area silhouette: placement still uses the distant-ground safety
    // checks, but echo's own candidate ring sits 7–12 blocks off the target
    // on their floor so the figure is actually visible.
    ECHO_01(
            0, "echo_01", 200, 4.0D, 175, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND, 40, 2, 600),
    THRESHOLD_01(
            1, "threshold_01", 160, 6.0D, 110, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND, 30, 2, 0),
    MOTION_ECHO_01(
            2, "motion_echo_01", 220, 5.0D, 225, true,
            ScenePlacementMode.CLIENT_MOTION_HISTORY, 20, 2, 0),
    LIGHT_FAULT_01(
            3, "light_fault_01", 140, 7.0D, 1_500, false,
            ScenePlacementMode.LOCAL_CAMERA_FOCUS, 0, 1, 0),
    // A silhouette that only reads at the edge of vision: the narrow gaze
    // cone and short dwell make a direct look resolve it within a beat —
    // long enough for the renderer's ~5-tick alpha ease to read as a
    // dissolve rather than a pop.
    PERIPHERAL_01(
            4, "peripheral_01", 140, 9.0D, 140, true,
            ScenePlacementMode.DISTANT_SAFE_GROUND, 25, 1, 500),
    // Sound-only: footsteps circle closer with no figure to look at, so the
    // dwell must outlast the TTL and the scene always ends in silence (TIMEOUT).
    FOOTSTEPS_01(
            5, "footsteps_01", 160, 360.0D, 60_000, false,
            ScenePlacementMode.DISTANT_SAFE_GROUND, 0, 1, 480),
    // An impossible second moon (or distant eyes) rendered only for the
    // target, high enough to clear terrain; a one-second direct look resolves
    // it. The anchor is the target itself — the mark hangs relative to them.
    SKY_MARK_01(
            6, "sky_mark_01", 240, 6.0D, 1_000, false,
            ScenePlacementMode.PLAYER_RELATIVE, 50, 1, 0),
    // A doorway that simply should not be there. It waits, and folds into
    // nothing as the target commits to approaching it. Resolved by approach,
    // never by gaze, so the dwell outlasts the TTL.
    FALSE_PASSAGE_01(
            7, "false_passage_01", 300, 360.0D, 60_000, false,
            ScenePlacementMode.DISTANT_SAFE_GROUND, 35, 2, 600),
    // A brief corrupted-recording tear across the target's own screen. Pure
    // GUI effect with strict intensity caps; no gaze, ends as TIMEOUT.
    CHROMA_BREAK_01(
            8, "chroma_break_01", 120, 360.0D, 60_000, false,
            ScenePlacementMode.PLAYER_RELATIVE, 0, 3, 0),
    // A figure that crosses just behind the target using their own motion
    // history; a fast glance dwell resolves it before it ever reaches the
    // crosshair.
    NEAR_MISS_01(
            9, "near_miss_01", 110, 5.0D, 120, true,
            ScenePlacementMode.CLIENT_MOTION_HISTORY, 0, 3, 0),
    // Sound-only: the target hears their own footsteps replayed about ten
    // seconds delayed from where they used to be. Never gaze-resolved.
    WHISPER_STEPS_01(
            10, "whisper_steps_01", 180, 360.0D, 60_000, false,
            ScenePlacementMode.CLIENT_MOTION_HISTORY, 0, 1, 0),
    // The far colossus: a ~100-block silhouette on the horizon, escalated one
    // stage per live Director trigger (the wire stage selects the distance).
    // Render-only — no entity, hitbox or loot — and never gaze-resolved: it
    // watches, the ground answers its footfalls, and the finale is simply
    // gone. The unease tier selects the heavy footfall-shake camera path.
    COLOSSUS_01(
            11, "colossus_01", 320, 360.0D, 60_000, false,
            ScenePlacementMode.HORIZON, 40, 3, 0),
    // The visitation: nothing renders in the world at all — the scare lives
    // outside the game window (a brief face blink, a wrong title, a small
    // window pulse), driven by the client OS-scare layer and bounded by
    // OsScareChoreography. Operator-only on the Director side; never
    // gaze-resolved, and the screen itself stays clean.
    VISITATION_01(
            12, "visitation_01", 70, 360.0D, 60_000, false,
            ScenePlacementMode.PLAYER_RELATIVE, 0, 0, 0),
    // Manifestation rift: target-private overlay family. Wire stage picks
    // eclipse (near-black), tear (chroma), unmoor (slow acid warp) or
    // witness (HUD gone, fullscreen eyes). Never gaze-resolved.
    RIFT_01(
            13, "rift_01", 200, 360.0D, 60_000, false,
            ScenePlacementMode.PLAYER_RELATIVE, 12, 3, 0);

    /**
     * Length of the single final beat that closes an encore, in ticks. The
     * server keeps the scene occupied for the whole delay plus this beat, so
     * a false all-clear can never overlap a second scene.
     */
    public static final int ENCORE_BEAT_TICKS = 30;

    private final int wireId;
    private final String serializedName;
    private final int defaultTtlTicks;
    private final double gazeAngleDegrees;
    private final int gazeDwellMillis;
    private final boolean rendersFigure;
    private final ScenePlacementMode placementMode;
    private final int preludeTicks;
    private final int uneaseLevel;
    private final int encoreDelayTicks;

    SceneProfile(
            int wireId,
            String serializedName,
            int defaultTtlTicks,
            double gazeAngleDegrees,
            int gazeDwellMillis,
            boolean rendersFigure,
            ScenePlacementMode placementMode,
            int preludeTicks,
            int uneaseLevel,
            int encoreDelayTicks) {
        this.wireId = wireId;
        this.serializedName = serializedName;
        this.defaultTtlTicks = defaultTtlTicks;
        this.gazeAngleDegrees = gazeAngleDegrees;
        this.gazeDwellMillis = gazeDwellMillis;
        this.rendersFigure = rendersFigure;
        this.placementMode = placementMode;
        this.preludeTicks = preludeTicks;
        this.uneaseLevel = uneaseLevel;
        this.encoreDelayTicks = encoreDelayTicks;
    }

    public int wireId() {
        return wireId;
    }

    public String serializedName() {
        return serializedName;
    }

    public int defaultTtlTicks() {
        return defaultTtlTicks;
    }

    public double gazeAngleDegrees() {
        return gazeAngleDegrees;
    }

    public int gazeDwellMillis() {
        return gazeDwellMillis;
    }

    public boolean rendersFigure() {
        return rendersFigure;
    }

    public boolean usesMotionHistory() {
        return placementMode == ScenePlacementMode.CLIENT_MOTION_HISTORY;
    }

    public ScenePlacementMode placementMode() {
        return placementMode;
    }

    /**
     * Ambience-dip length before the scene body: the world dims and a low
     * swell builds, but nothing is shown and no acknowledgement is sent.
     */
    public int preludeTicks() {
        return preludeTicks;
    }

    /** Camera-perturbation intensity tier, 0 (still) to 3 (strongest). */
    public int uneaseLevel() {
        return uneaseLevel;
    }

    /**
     * Silent gap after the scene body before one final beat fires, in ticks.
     * Zero means the scene truly ends when its body resolves.
     */
    public int encoreDelayTicks() {
        return encoreDelayTicks;
    }

    /**
     * Highest wire stage this profile may carry. Zero means the stage field
     * must stay at the default; colossus, haunt and rift escalate inside it.
     */
    public int maxStage() {
        return switch (this) {
            case COLOSSUS_01 -> ColossusChoreography.MAX_STAGE;
            case RIFT_01 -> RiftChoreography.MAX_STAGE;
            case FOOTSTEPS_01 -> HauntChoreography.MAX_STAGE;
            default -> 0;
        };
    }

    /**
     * Ticks the server must consider the scene occupied for: body TTL plus
     * the full encore, so the one-active-scene invariant survives a false
     * all-clear even if the client's terminal acknowledgement never arrives.
     */
    public int occupancyTicks(int ttlTicks) {
        int occupancy = ttlTicks + 20;
        if (encoreDelayTicks > 0) {
            occupancy += encoreDelayTicks + ENCORE_BEAT_TICKS;
        }
        return occupancy;
    }

    /**
     * Server-side acknowledgement allowlist. In particular, visitation has
     * no in-game visibility or gaze resolution, so a forged VISIBLE cannot
     * claim presentation and a forged terminal GAZE cannot clear its slot.
     */
    public boolean acceptsAcknowledgement(SceneAck acknowledgement) {
        return switch (acknowledgement) {
            case VISIBLE -> this != VISITATION_01;
            case GAZE -> switch (this) {
                case ECHO_01,
                        THRESHOLD_01,
                        MOTION_ECHO_01,
                        LIGHT_FAULT_01,
                        PERIPHERAL_01,
                        SKY_MARK_01,
                        FALSE_PASSAGE_01,
                        NEAR_MISS_01 -> true;
                default -> false;
            };
            default -> true;
        };
    }

    public static SceneProfile fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(profile -> profile.wireId == wireId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scene profile wire id: " + wireId));
    }

    public static SceneProfile parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(profile -> profile.serializedName.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown scene profile: " + value));
    }
}
