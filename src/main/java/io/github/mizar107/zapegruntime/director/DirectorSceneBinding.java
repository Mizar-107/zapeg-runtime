package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One data-pack-authored story-scene binding consumed by the native Director. */
public record DirectorSceneBinding(
        StoryFactType factType,
        ResourceLocation subject,
        SceneProfile profile,
        int ttlTicks,
        int stage,
        int presentationVariant,
        int cooldownTicks,
        int retryTicks,
        String fingerprint) {

    public static final int MIN_COOLDOWN_TICKS = 100;
    public static final int MAX_COOLDOWN_TICKS = 72_000;
    public static final int MIN_RETRY_TICKS = 20;
    public static final int MAX_RETRY_TICKS = 1_200;

    public DirectorSceneBinding(
            StoryFactType factType,
            ResourceLocation subject,
            SceneProfile profile,
            int ttlTicks,
            int stage,
            int presentationVariant,
            int cooldownTicks,
            int retryTicks) {
        this(
                factType,
                subject,
                profile,
                ttlTicks,
                stage,
                presentationVariant,
                cooldownTicks,
                retryTicks,
                "");
    }

    public DirectorSceneBinding {
        Objects.requireNonNull(factType, "factType");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(profile, "profile");
        if (factType != StoryFactType.SCENE_COMPLETED
                && factType != StoryFactType.SCENE_PRESENTED) {
            throw new IllegalArgumentException("Director bindings require a scene fact type");
        }
        if (ttlTicks < SceneDescriptor.MIN_TTL_TICKS
                || ttlTicks > SceneDescriptor.MAX_TTL_TICKS) {
            throw new IllegalArgumentException("Director scene TTL is outside the wire bounds");
        }
        if (stage < 0 || stage > profile.maxStage()) {
            throw new IllegalArgumentException(
                    "Director stage is not meaningful for " + profile.serializedName());
        }
        if (presentationVariant < 0 || presentationVariant > 15) {
            throw new IllegalArgumentException(
                    "Director presentation variant must be between 0 and 15");
        }
        if (cooldownTicks < MIN_COOLDOWN_TICKS
                || cooldownTicks > MAX_COOLDOWN_TICKS) {
            throw new IllegalArgumentException("Director cooldown is outside the supported bounds");
        }
        if (retryTicks < MIN_RETRY_TICKS || retryTicks > MAX_RETRY_TICKS) {
            throw new IllegalArgumentException("Director retry delay is outside the supported bounds");
        }
        String canonical = calculateFingerprint(
                factType,
                subject,
                profile,
                ttlTicks,
                stage,
                presentationVariant,
                cooldownTicks,
                retryTicks);
        if (!fingerprint.isEmpty() && !canonical.equals(fingerprint)) {
            throw new IllegalArgumentException("Director binding fingerprint is not canonical");
        }
        fingerprint = canonical;
    }

    public StoryTrigger trigger() {
        return new StoryTrigger(factType, subject);
    }

    /** Canonical choreography signature used to reject accidental duplicate scenes. */
    public String presentationSignature() {
        return profile.serializedName() + ':' + stage + ':' + ttlTicks + ':'
                + presentationVariant;
    }

    private static String calculateFingerprint(
            StoryFactType factType,
            ResourceLocation subject,
            SceneProfile profile,
            int ttlTicks,
            int stage,
            int presentationVariant,
            int cooldownTicks,
            int retryTicks) {
        String canonical = factType.serializedName() + '|' + subject + '|'
                + profile.serializedName() + '|' + ttlTicks + '|' + stage + '|'
                + presentationVariant + '|'
                + cooldownTicks + '|' + retryTicks;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
