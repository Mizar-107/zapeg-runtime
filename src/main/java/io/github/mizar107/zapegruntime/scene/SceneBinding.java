package io.github.mizar107.zapegruntime.scene;

import java.util.Locale;
import java.util.Objects;

/**
 * Public command name → (wire profile, default stage). Duplicate beats are
 * aliases into a family rather than parallel profiles; an explicit stage
 * argument still overrides the alias default.
 */
public record SceneBinding(SceneProfile profile, int stage) {

    public SceneBinding {
        Objects.requireNonNull(profile, "profile");
        if (stage < 0 || stage > profile.maxStage()) {
            throw new IllegalArgumentException(
                    "Scene stage must be between 0 and " + profile.maxStage()
                            + " for " + profile.serializedName());
        }
    }

    /**
     * Parses a command-tree word. Unknown names fail closed via
     * {@link SceneProfile#parse(String)}.
     */
    public static SceneBinding parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "eclipse_01", "light_fault_01" ->
                    new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_ECLIPSE);
            case "chroma_break_01", "tear_01" ->
                    new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_TEAR);
            case "unmoor_01" ->
                    new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_UNMOOR);
            case "witness_01" ->
                    new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_WITNESS);
            case "rift_01" ->
                    new SceneBinding(SceneProfile.RIFT_01, RiftChoreography.STAGE_ECLIPSE);
            case "whisper_steps_01" ->
                    new SceneBinding(
                            SceneProfile.FOOTSTEPS_01, HauntChoreography.STAGE_WHISPER);
            case "closing_steps_01" ->
                    new SceneBinding(
                            SceneProfile.FOOTSTEPS_01, HauntChoreography.STAGE_CLOSING);
            default -> new SceneBinding(SceneProfile.parse(normalized), 0);
        };
    }

    public SceneBinding withStage(int explicitStage) {
        return new SceneBinding(profile, explicitStage);
    }
}
