package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.scene.SceneDescriptor;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import io.github.mizar107.zapegruntime.story.StoryCampaignRegistry;
import io.github.mizar107.zapegruntime.story.StoryFactType;
import io.github.mizar107.zapegruntime.story.StoryTrigger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Closed, rehearsal-only view of the two native Voice Director bindings. */
public record VoiceRehearsalPlan(
        ResourceLocation subject,
        SceneProfile profile,
        int ttlTicks,
        int stage,
        int presentationVariant) {

    public static final ResourceLocation VOICE_01 =
            ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "voice_01");
    public static final ResourceLocation VOICE_02 =
            ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "voice_02");
    private static final List<ResourceLocation> SUPPORTED_SUBJECTS =
            List.of(VOICE_01, VOICE_02);

    public VoiceRehearsalPlan {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(profile, "profile");
        if (!SUPPORTED_SUBJECTS.contains(subject)) {
            throw new IllegalArgumentException("unsupported Voice rehearsal subject");
        }
        if (profile != SceneProfile.BREACH_01) {
            throw new IllegalArgumentException("Voice rehearsal requires breach_01");
        }
        if (ttlTicks < SceneDescriptor.MIN_TTL_TICKS
                || ttlTicks > SceneDescriptor.MAX_TTL_TICKS) {
            throw new IllegalArgumentException("Voice rehearsal TTL is outside wire bounds");
        }
        if (stage != 0) {
            throw new IllegalArgumentException("Voice rehearsal requires stage zero");
        }
        if (presentationVariant < 0 || presentationVariant > 15) {
            throw new IllegalArgumentException("invalid Voice presentation variant");
        }
    }

    public static List<ResourceLocation> supportedSubjects() {
        return SUPPORTED_SUBJECTS;
    }

    public static Optional<VoiceRehearsalPlan> resolve(
            DirectorSceneRegistry.Snapshot registry,
            ResourceLocation subject) {
        Objects.requireNonNull(registry, "registry");
        if (!SUPPORTED_SUBJECTS.contains(subject)) {
            return Optional.empty();
        }
        return registry.find(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                .flatMap(catalog -> resolve(catalog, subject));
    }

    static Optional<VoiceRehearsalPlan> resolve(
            DirectorSceneCatalog catalog,
            ResourceLocation subject) {
        Objects.requireNonNull(catalog, "catalog");
        if (!catalog.campaignId().equals(StoryCampaignRegistry.HERALDOR_CAMPAIGN)
                || !SUPPORTED_SUBJECTS.contains(subject)) {
            return Optional.empty();
        }
        StoryTrigger trigger = new StoryTrigger(StoryFactType.SCENE_COMPLETED, subject);
        return catalog.find(trigger).flatMap(binding -> fromBinding(subject, binding));
    }

    static Optional<VoiceRehearsalPlan> fromBinding(
            ResourceLocation subject,
            DirectorSceneBinding binding) {
        if (!SUPPORTED_SUBJECTS.contains(subject)
                || binding.factType() != StoryFactType.SCENE_COMPLETED
                || !binding.subject().equals(subject)
                || binding.profile() != SceneProfile.BREACH_01
                || binding.stage() != 0) {
            return Optional.empty();
        }
        return Optional.of(new VoiceRehearsalPlan(
                subject,
                binding.profile(),
                binding.ttlTicks(),
                binding.stage(),
                binding.presentationVariant()));
    }

    /** Exact audio-choice seed used by the corresponding automatic Director scene. */
    public long visualSeed(UUID eventId) {
        return DirectorSceneIdentity.authoredVisualSeed(eventId, presentationVariant);
    }
}
