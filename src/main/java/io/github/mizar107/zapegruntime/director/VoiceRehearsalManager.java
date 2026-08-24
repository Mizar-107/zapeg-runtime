package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.server.SceneServerManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Operator rehearsal coordinator; it owns no campaign or network authority. */
public final class VoiceRehearsalManager {

    private static final VoiceRehearsalStatusLedger STATUSES =
            new VoiceRehearsalStatusLedger();

    private VoiceRehearsalManager() {}

    public record StartResult(
            boolean success,
            boolean alreadyActive,
            UUID eventId,
            String message) {}

    public static StartResult rehearse(ServerPlayer target, ResourceLocation subject) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(subject, "subject");
        UUID targetId = target.getUUID();
        Optional<VoiceRehearsalStatusLedger.Entry> previous = STATUSES.get(targetId);
        if (previous.isPresent() && previous.get().active()) {
            VoiceRehearsalStatusLedger.Entry active = previous.get();
            return new StartResult(
                    true,
                    true,
                    active.eventId(),
                    "voice rehearsal already active " + active.compactString());
        }

        UUID eventId = UUID.randomUUID();
        if (!STATUSES.reserve(targetId, eventId, subject)) {
            return new StartResult(
                    false,
                    false,
                    eventId,
                    "voice rehearsal rejected detail=status_capacity_exhausted");
        }

        Optional<VoiceRehearsalPlan> resolved =
                VoiceRehearsalPlan.resolve(DirectorSceneRegistry.current(), subject);
        if (resolved.isEmpty()) {
            STATUSES.failed(
                    targetId,
                    eventId,
                    VoiceRehearsalStatusLedger.State.REJECTED,
                    "binding unavailable");
            ZapeGRuntime.LOGGER.warn(
                    "Rejected Voice rehearsal target={} subject={} reason=binding_unavailable",
                    targetId,
                    subject);
            return new StartResult(
                    false,
                    false,
                    eventId,
                    "voice rehearsal rejected " + STATUSES.statusFor(targetId));
        }

        VoiceRehearsalPlan plan = resolved.get();
        if (!STATUSES.bind(targetId, eventId, plan)) {
            STATUSES.failed(
                    targetId,
                    eventId,
                    VoiceRehearsalStatusLedger.State.REJECTED,
                    "status reservation lost");
            return new StartResult(
                    false,
                    false,
                    eventId,
                    "voice rehearsal rejected detail=status_reservation_lost");
        }

        SceneServerManager.DispatchResult dispatch =
                SceneServerManager.dispatchVoiceRehearsal(target, eventId, plan);
        if (!dispatch.success()) {
            VoiceRehearsalStatusLedger.State state =
                    "target already has an active scene".equals(dispatch.message())
                            ? VoiceRehearsalStatusLedger.State.BUSY
                            : VoiceRehearsalStatusLedger.State.REJECTED;
            STATUSES.failed(targetId, eventId, state, dispatch.message());
            ZapeGRuntime.LOGGER.warn(
                    "Rejected Voice rehearsal target={} subject={} event={} reason={}",
                    targetId,
                    subject,
                    eventId,
                    dispatch.message());
            return new StartResult(
                    false,
                    false,
                    eventId,
                    "voice rehearsal rejected " + STATUSES.statusFor(targetId));
        }

        STATUSES.dispatched(targetId, eventId);
        ZapeGRuntime.LOGGER.info(
                "Dispatched Voice rehearsal target={} subject={} event={} variant={}",
                targetId,
                subject,
                eventId,
                plan.presentationVariant());
        return new StartResult(
                true,
                false,
                eventId,
                "voice rehearsal dispatched " + STATUSES.statusFor(targetId));
    }

    public static String statusFor(UUID targetId) {
        return STATUSES.statusFor(targetId);
    }

    public static void onAcknowledgement(
            UUID targetId,
            UUID eventId,
            SceneAck acknowledgement) {
        STATUSES.acknowledge(targetId, eventId, acknowledgement);
    }

    public static void onCancelled(
            UUID targetId,
            UUID eventId,
            CancelReason reason) {
        STATUSES.cancelled(targetId, eventId, reason);
    }

    public static void clearTarget(UUID targetId) {
        STATUSES.clear(targetId);
    }

    public static void shutdown() {
        STATUSES.clear();
    }

    static void resetForTests() {
        STATUSES.clear();
    }
}
