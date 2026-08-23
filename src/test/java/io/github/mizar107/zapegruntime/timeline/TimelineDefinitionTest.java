package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimelineDefinitionTest {

    @Test
    void canonicalOrderAndFingerprintIgnoreAuthoredArrayOrder() {
        TimelineAction later = action("later", 40, 80);
        TimelineAction earlierB = action("b", 10, 30);
        TimelineAction earlierA = action("a", 10, 30);
        TimelineDefinition first = definition(List.of(later, earlierB, earlierA));
        TimelineDefinition second = definition(List.of(earlierA, later, earlierB));

        assertEquals(List.of("a", "b", "later"), first.actions().stream()
                .map(TimelineAction::id)
                .toList());
        assertEquals(first.actions(), second.actions());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.actions().add(later));
    }

    @Test
    void everySemanticFieldAffectsFingerprint() {
        TimelineDefinition baseline = definition(List.of(action("cue", 10, 30)));
        TimelineDefinition changed = new TimelineDefinition(
                baseline.id(),
                baseline.durationTicks(),
                baseline.policies(),
                List.of(new TimelineAction(
                        "cue", 10, 31, 5, true, SceneProfile.ECHO_01, 200, 0)));

        assertNotEquals(baseline.fingerprint(), changed.fingerprint());
    }

    @Test
    void duplicateAndOutOfBoundsActionsFailClosed() {
        TimelineAction duplicate = action("same", 10, 30);
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(List.of(duplicate, duplicate)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimelineDefinition(
                        id(),
                        20,
                        policies(),
                        List.of(action("late", 10, 21))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimelineAction(
                        "bad stage", 0, 10, 1, true, SceneProfile.ECHO_01, 200, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TimelineAction(
                        "stage", 0, 10, 1, true, SceneProfile.ECHO_01, 200, 1));
    }

    @Test
    void deterministicIdsAndSeedsAreTargetAndActionScoped() {
        TimelineDefinition definition = definition(List.of(
                action("first", 10, 30), action("second", 40, 60)));
        TimelineAction first = definition.actions().get(0);
        TimelineAction second = definition.actions().get(1);
        java.util.UUID session = java.util.UUID.fromString(
                "11111111-2222-3333-4444-555555555555");
        java.util.UUID target = java.util.UUID.fromString(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        long seed = TimelineDeterminism.sessionSeed(session, target, definition);
        assertEquals(seed, TimelineDeterminism.sessionSeed(session, target, definition));
        assertNotEquals(
                seed,
                TimelineDeterminism.sessionSeed(
                        session, java.util.UUID.randomUUID(), definition));
        assertEquals(
                TimelineDeterminism.actionEventId(session, target, definition, first),
                TimelineDeterminism.actionEventId(session, target, definition, first));
        assertNotEquals(
                TimelineDeterminism.actionEventId(session, target, definition, first),
                TimelineDeterminism.actionEventId(session, target, definition, second));
        assertNotEquals(
                TimelineDeterminism.actionEventId(session, target, definition, first),
                TimelineDeterminism.actionEventId(
                        session, java.util.UUID.randomUUID(), definition, first));
        assertNotEquals(
                TimelineDeterminism.actionSeed(seed, definition, first),
                TimelineDeterminism.actionSeed(seed, definition, second));
        assertNotEquals(
                TimelineDeterminism.actionSeed(seed, definition, first),
                TimelineDeterminism.placementSeed(seed, definition, first));
        assertEquals(
                TimelineDeterminism.placementSeed(seed, definition, first),
                TimelineDeterminism.placementSeed(seed, definition, first));
        assertTrue(TimelineDeterminism.actionEventId(session, target, definition, first)
                .version() == 3);
    }

    static TimelineDefinition definition(List<TimelineAction> actions) {
        return new TimelineDefinition(id(), 100, policies(), actions);
    }

    static TimelinePolicies policies() {
        return new TimelinePolicies(
                TimelinePolicies.Disconnect.PAUSE,
                TimelinePolicies.Restart.PAUSE,
                TimelinePolicies.DimensionChange.FAIL,
                TimelinePolicies.Death.FAIL);
    }

    static TimelineAction action(String id, int at, int deadline) {
        return new TimelineAction(
                id, at, deadline, 5, true, SceneProfile.ECHO_01, 200, 0);
    }

    static ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("zapeg_runtime", "test_timeline");
    }
}
