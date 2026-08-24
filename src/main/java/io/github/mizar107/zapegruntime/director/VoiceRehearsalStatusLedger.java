package io.github.mizar107.zapegruntime.director;

import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.scene.SceneAck;
import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Bounded JVM-local truth for the latest native Voice rehearsal per target. */
final class VoiceRehearsalStatusLedger {

    static final int DEFAULT_MAX_ENTRIES = 128;

    enum State {
        PENDING(true),
        DISPATCHED(true),
        RECEIVED(true),
        VISIBLE(true),
        GAZE(false),
        TIMEOUT(false),
        ABORTED(false),
        BUSY(false),
        REJECTED(false),
        EXPIRED(false),
        CANCELLED(false);

        private final boolean active;

        State(boolean active) {
            this.active = active;
        }

        boolean active() {
            return active;
        }
    }

    record Entry(
            UUID targetId,
            UUID eventId,
            ResourceLocation subject,
            SceneProfile profile,
            int ttlTicks,
            int stage,
            int presentationVariant,
            State state,
            SceneAck lastAcknowledgement,
            boolean visible,
            String detail) {

        Entry {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(detail, "detail");
        }

        boolean active() {
            return state.active();
        }

        Entry withPlan(VoiceRehearsalPlan plan) {
            return new Entry(
                    targetId,
                    eventId,
                    plan.subject(),
                    plan.profile(),
                    plan.ttlTicks(),
                    plan.stage(),
                    plan.presentationVariant(),
                    state,
                    lastAcknowledgement,
                    visible,
                    detail);
        }

        Entry withState(State nextState, SceneAck acknowledgement, boolean sawVisible,
                String nextDetail) {
            return new Entry(
                    targetId,
                    eventId,
                    subject,
                    profile,
                    ttlTicks,
                    stage,
                    presentationVariant,
                    nextState,
                    acknowledgement,
                    visible || sawVisible,
                    nextDetail);
        }

        String compactString() {
            StringBuilder result = new StringBuilder("voice delivery=native_target_private")
                    .append(" active=").append(active() ? 1 : 0)
                    .append(" subject=").append(subject)
                    .append(active() ? " event=" : " last_event=").append(eventId)
                    .append(" profile=")
                    .append(profile == null ? "none" : profile.serializedName())
                    .append(" ttl=").append(ttlTicks)
                    .append(" stage=").append(stage)
                    .append(" variant=").append(presentationVariant)
                    .append(" state=").append(state.name().toLowerCase(Locale.ROOT))
                    .append(" ack=").append(lastAcknowledgement == null
                            ? "none"
                            : lastAcknowledgement.name().toLowerCase(Locale.ROOT))
                    .append(" visible=").append(visible ? 1 : 0);
            if (!detail.isEmpty()) {
                result.append(" detail=").append(detail.replace(' ', '_'));
            }
            return result.toString();
        }
    }

    private final int maxEntries;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();

    VoiceRehearsalStatusLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    VoiceRehearsalStatusLedger(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("Voice status bound must be positive");
        }
        this.maxEntries = maxEntries;
    }

    boolean reserve(UUID targetId, UUID eventId, ResourceLocation subject) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(subject, "subject");
        if (!makeRoomFor(targetId)) {
            return false;
        }
        entries.put(targetId, new Entry(
                targetId,
                eventId,
                subject,
                null,
                -1,
                -1,
                -1,
                State.PENDING,
                null,
                false,
                ""));
        return true;
    }

    boolean bind(UUID targetId, UUID eventId, VoiceRehearsalPlan plan) {
        Entry current = matching(targetId, eventId);
        if (current == null || !current.active()) {
            return false;
        }
        entries.put(targetId, current.withPlan(plan));
        return true;
    }

    void dispatched(UUID targetId, UUID eventId) {
        updateState(targetId, eventId, State.DISPATCHED, null, false, "");
    }

    void failed(UUID targetId, UUID eventId, State state, String detail) {
        if (state != State.BUSY && state != State.REJECTED) {
            throw new IllegalArgumentException("invalid Voice dispatch failure state");
        }
        updateState(targetId, eventId, state, null, false, detail);
    }

    void acknowledge(UUID targetId, UUID eventId, SceneAck acknowledgement) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        Entry current = matching(targetId, eventId);
        if (current == null || !current.active()) {
            return;
        }
        if (current.state() == State.VISIBLE && acknowledgement == SceneAck.RECEIVED) {
            return;
        }
        State state = switch (acknowledgement) {
            case RECEIVED -> State.RECEIVED;
            case VISIBLE -> State.VISIBLE;
            case GAZE -> State.GAZE;
            case TIMEOUT -> State.TIMEOUT;
            case ABORTED -> State.ABORTED;
            case BUSY -> State.BUSY;
            case REJECTED -> State.REJECTED;
        };
        updateState(
                targetId,
                eventId,
                state,
                acknowledgement,
                acknowledgement == SceneAck.VISIBLE,
                "");
    }

    void cancelled(UUID targetId, UUID eventId, CancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason == CancelReason.LOGOUT
                || reason == CancelReason.DEATH
                || reason == CancelReason.DIMENSION_CHANGE
                || reason == CancelReason.SERVER_STOP) {
            clear(targetId);
            return;
        }
        updateState(
                targetId,
                eventId,
                reason == CancelReason.EXPIRED ? State.EXPIRED : State.CANCELLED,
                null,
                false,
                reason.name().toLowerCase(Locale.ROOT));
    }

    Optional<Entry> get(UUID targetId) {
        return Optional.ofNullable(entries.get(targetId));
    }

    String statusFor(UUID targetId) {
        Entry entry = entries.get(targetId);
        return entry == null
                ? "voice delivery=native_target_private active=0 last=none"
                : entry.compactString();
    }

    int size() {
        return entries.size();
    }

    void clear(UUID targetId) {
        entries.remove(targetId);
    }

    void clear() {
        entries.clear();
    }

    private void updateState(
            UUID targetId,
            UUID eventId,
            State state,
            SceneAck acknowledgement,
            boolean visible,
            String detail) {
        Entry current = matching(targetId, eventId);
        if (current == null || !current.active()) {
            return;
        }
        entries.put(
                targetId,
                current.withState(state, acknowledgement, visible, detail));
    }

    private Entry matching(UUID targetId, UUID eventId) {
        Entry current = entries.get(targetId);
        return current != null && current.eventId().equals(eventId) ? current : null;
    }

    private boolean makeRoomFor(UUID targetId) {
        Entry existing = entries.get(targetId);
        if (existing != null) {
            return !existing.active();
        }
        if (entries.size() < maxEntries) {
            return true;
        }
        Iterator<Map.Entry<UUID, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> candidate = iterator.next();
            if (!candidate.getValue().active()) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
