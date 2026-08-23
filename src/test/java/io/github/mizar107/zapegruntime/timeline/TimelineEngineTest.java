package io.github.mizar107.zapegruntime.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.scene.SceneProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TimelineEngineTest {

    private static final UUID SESSION = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld");
    private static final ResourceLocation NETHER = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "the_nether");

    @Test
    void dispatchesOnlyAtAuthoredTickWithStablePrivateInvocation() {
        TimelineDefinition definition = definition(
                TimelineDefinitionTest.policies(), action("cue", 3, 20, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        List<TimelineEngine.ActionInvocation> invocations = new ArrayList<>();

        TimelineEngine.Step first = tick(
                session, definition, invocations, TimelineEngine.ActionOutcome.APPLIED);
        TimelineEngine.Step second = tick(
                first.active(), definition, invocations, TimelineEngine.ActionOutcome.APPLIED);
        TimelineEngine.Step third = tick(
                second.active(), definition, invocations, TimelineEngine.ActionOutcome.APPLIED);

        assertFalse(first.finished());
        assertFalse(second.finished());
        assertTrue(third.finished());
        assertEquals(TimelineEngine.TerminalStatus.SUCCEEDED, third.terminal().status());
        assertEquals(1, invocations.size());
        TimelineEngine.ActionInvocation invocation = invocations.get(0);
        assertEquals(SESSION, invocation.sessionId());
        assertEquals(TARGET, invocation.targetId());
        assertEquals(
                TimelineDeterminism.actionEventId(
                        SESSION, definition, definition.actions().get(0)),
                invocation.eventId());
        assertEquals(
                TimelineDeterminism.actionSeed(
                        session.seed(), definition, definition.actions().get(0)),
                invocation.visualSeed());
    }

    @Test
    void retryScheduleIsBoundedAndDoesNotReorderFollowingActions() {
        TimelineDefinition definition = definition(
                TimelineDefinitionTest.policies(),
                new TimelineAction(
                        "first", 1, 20, 5, true, SceneProfile.ECHO_01, 200, 0),
                action("second", 1, 30, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        List<String> invoked = new ArrayList<>();

        TimelineEngine.Step step = TimelineEngine.tick(
                session,
                definition,
                online(OVERWORLD),
                invocation -> {
                    invoked.add(invocation.action().id());
                    return invoked.size() == 1
                            ? TimelineEngine.ActionOutcome.RETRYABLE
                            : TimelineEngine.ActionOutcome.APPLIED;
                });
        assertEquals(1, step.active().actionAttempts());
        assertEquals(6, step.active().retryAtElapsedTick());
        for (int tick = 2; tick <= 5; tick++) {
            step = TimelineEngine.tick(
                    step.active(),
                    definition,
                    online(OVERWORLD),
                    invocation -> {
                        invoked.add(invocation.action().id());
                        return TimelineEngine.ActionOutcome.APPLIED;
                    });
        }
        assertEquals(List.of("first"), invoked);
        step = TimelineEngine.tick(
                step.active(),
                definition,
                online(OVERWORLD),
                invocation -> {
                    invoked.add(invocation.action().id());
                    return TimelineEngine.ActionOutcome.APPLIED;
                });
        assertFalse(step.finished());
        assertEquals(1, step.active().nextActionIndex());
        step = TimelineEngine.tick(
                step.active(),
                definition,
                online(OVERWORLD),
                invocation -> {
                    invoked.add(invocation.action().id());
                    return TimelineEngine.ActionOutcome.ALREADY_APPLIED;
                });
        assertTrue(step.finished());
        assertEquals(List.of("first", "first", "second"), invoked);
    }

    @Test
    void requiredFailuresAreTerminalAndOptionalFailuresSkip() {
        TimelineDefinition required = definition(
                TimelineDefinitionTest.policies(), action("required", 1, 10, true));
        TimelineEngine.Step rejected = TimelineEngine.tick(
                TimelineSession.start(SESSION, TARGET, required, OVERWORLD),
                required,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.REJECTED);
        assertEquals(TimelineEngine.TerminalStatus.FAILED, rejected.terminal().status());
        assertEquals(TimelineEngine.TerminalReason.ACTION_REJECTED, rejected.terminal().reason());

        TimelineDefinition optional = definition(
                TimelineDefinitionTest.policies(), action("optional", 1, 10, false));
        TimelineEngine.Step skipped = TimelineEngine.tick(
                TimelineSession.start(SESSION, TARGET, optional, OVERWORLD),
                optional,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.REJECTED);
        assertEquals(TimelineEngine.TerminalStatus.SUCCEEDED, skipped.terminal().status());
    }

    @Test
    void disconnectAndRestartPauseWithoutAdvancingThenResume() {
        TimelineDefinition definition = definition(
                TimelineDefinitionTest.policies(), action("cue", 5, 20, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineEngine.Step initial = TimelineEngine.tick(
                session,
                definition,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        TimelineEngine.Step disconnected = TimelineEngine.tick(
                initial.active(),
                definition,
                TimelineEngine.PlayerState.offline(),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(1, disconnected.active().elapsedTicks());
        assertEquals(
                TimelineSession.Status.PAUSED_DISCONNECT,
                disconnected.active().status());

        TimelineSession restarted = TimelineEngine.pauseForRestart(disconnected.active());
        TimelineEngine.Step stillOffline = TimelineEngine.tick(
                restarted,
                definition,
                TimelineEngine.PlayerState.offline(),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(1, stillOffline.active().elapsedTicks());
        assertEquals(TimelineSession.Status.PAUSED_RESTART, stillOffline.active().status());
        TimelineEngine.Step resumed = TimelineEngine.tick(
                stillOffline.active(),
                definition,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(2, resumed.active().elapsedTicks());
        assertEquals(TimelineSession.Status.RUNNING, resumed.active().status());
    }

    @Test
    void configuredLifecycleFailuresAndPausesAreExplicit() {
        TimelinePolicies strict = new TimelinePolicies(
                TimelinePolicies.Disconnect.FAIL,
                TimelinePolicies.Restart.FAIL,
                TimelinePolicies.DimensionChange.FAIL,
                TimelinePolicies.Death.CANCEL);
        TimelineDefinition definition = definition(strict, action("cue", 5, 20, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);

        assertEquals(
                TimelineEngine.TerminalReason.DISCONNECTED,
                TimelineEngine.tick(
                                session,
                                definition,
                                TimelineEngine.PlayerState.offline(),
                                ignored -> TimelineEngine.ActionOutcome.APPLIED)
                        .terminal().reason());
        assertEquals(
                TimelineEngine.TerminalReason.SERVER_RESTART,
                TimelineEngine.tick(
                                TimelineEngine.pauseForRestart(session),
                                definition,
                                online(OVERWORLD),
                                ignored -> TimelineEngine.ActionOutcome.APPLIED)
                        .terminal().reason());
        assertEquals(
                TimelineEngine.TerminalReason.DIMENSION_CHANGED,
                TimelineEngine.tick(
                                session,
                                definition,
                                online(NETHER),
                                ignored -> TimelineEngine.ActionOutcome.APPLIED)
                        .terminal().reason());
        TimelineEngine.Step death = TimelineEngine.tick(
                session,
                definition,
                new TimelineEngine.PlayerState(true, false, false, OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(TimelineEngine.TerminalStatus.CANCELLED, death.terminal().status());
        assertEquals(TimelineEngine.TerminalReason.TARGET_DIED, death.terminal().reason());
    }

    @Test
    void dimensionPauseResumesOnlyInBoundDimension() {
        TimelinePolicies policies = new TimelinePolicies(
                TimelinePolicies.Disconnect.PAUSE,
                TimelinePolicies.Restart.PAUSE,
                TimelinePolicies.DimensionChange.PAUSE,
                TimelinePolicies.Death.FAIL);
        TimelineDefinition definition = definition(policies, action("cue", 3, 20, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);

        TimelineEngine.Step away = TimelineEngine.tick(
                session,
                definition,
                online(NETHER),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(TimelineSession.Status.PAUSED_DIMENSION, away.active().status());
        assertEquals(0, away.active().elapsedTicks());
        TimelineEngine.Step home = TimelineEngine.tick(
                away.active(),
                definition,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.APPLIED);
        assertEquals(TimelineSession.Status.RUNNING, home.active().status());
        assertEquals(1, home.active().elapsedTicks());
    }

    @Test
    void changedDefinitionFailsBeforeAnyActionRuns() {
        TimelineDefinition original = definition(
                TimelineDefinitionTest.policies(), action("cue", 1, 20, true));
        TimelineDefinition changed = definition(
                TimelineDefinitionTest.policies(), action("cue", 2, 20, true));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, original, OVERWORLD);
        List<TimelineEngine.ActionInvocation> invocations = new ArrayList<>();

        TimelineEngine.Step step = TimelineEngine.tick(
                session,
                changed,
                online(OVERWORLD),
                invocation -> {
                    invocations.add(invocation);
                    return TimelineEngine.ActionOutcome.APPLIED;
                });

        assertTrue(step.finished());
        assertEquals(TimelineEngine.TerminalReason.DEFINITION_CHANGED, step.terminal().reason());
        assertTrue(invocations.isEmpty());
    }

    @Test
    void retryLimitAndDeadlineCannotRunForever() {
        TimelineDefinition definition = definition(
                TimelineDefinitionTest.policies(),
                new TimelineAction(
                        "cue", 1, 2, 1, true, SceneProfile.ECHO_01, 200, 0));
        TimelineSession session = TimelineSession.start(
                SESSION, TARGET, definition, OVERWORLD);
        TimelineEngine.Step first = TimelineEngine.tick(
                session,
                definition,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.RETRYABLE);
        TimelineEngine.Step second = TimelineEngine.tick(
                first.active(),
                definition,
                online(OVERWORLD),
                ignored -> TimelineEngine.ActionOutcome.RETRYABLE);
        assertEquals(TimelineEngine.TerminalReason.ACTION_DEADLINE, second.terminal().reason());
    }

    private static TimelineEngine.Step tick(
            TimelineSession session,
            TimelineDefinition definition,
            List<TimelineEngine.ActionInvocation> invocations,
            TimelineEngine.ActionOutcome result) {
        assertNotNull(session);
        return TimelineEngine.tick(
                session,
                definition,
                online(OVERWORLD),
                invocation -> {
                    invocations.add(invocation);
                    return result;
                });
    }

    private static TimelineEngine.PlayerState online(ResourceLocation dimension) {
        return new TimelineEngine.PlayerState(true, true, false, dimension);
    }

    private static TimelineDefinition definition(
            TimelinePolicies policies, TimelineAction... actions) {
        return new TimelineDefinition(
                TimelineDefinitionTest.id(), 100, policies, List.of(actions));
    }

    private static TimelineAction action(
            String id, int atTick, int deadlineTick, boolean required) {
        return new TimelineAction(
                id,
                atTick,
                deadlineTick,
                5,
                required,
                SceneProfile.ECHO_01,
                200,
                0);
    }
}
