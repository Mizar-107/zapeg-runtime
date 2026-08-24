package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.encounter.NinthFormEncounterManager;
import io.github.mizar107.zapegruntime.director.HeraldorDirector;
import io.github.mizar107.zapegruntime.quest.QuestServerEvents;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.servant.ServantEncounterManager;
import io.github.mizar107.zapegruntime.timeline.TimelineServerManager;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import net.minecraft.server.MinecraftServer;

/** One fail-closed authority query and one ordered emergency-cleanup coordinator. */
public final class HeraldorSafetyController {

    private static final Map<MinecraftServer, Long> ENFORCED_GENERATIONS = new WeakHashMap<>();

    private HeraldorSafetyController() {}

    public static HeraldorSafetyMode effectiveMode(MinecraftServer server) {
        if (server == null) {
            return HeraldorSafetyMode.QUARANTINED;
        }
        return HeraldorSafetyData.get(server).effectiveMode(HeraldorSafetyCeiling.current());
    }

    public static boolean allows(MinecraftServer server, HeraldorSafetyMode required) {
        return server != null && effectiveMode(server).allows(required);
    }

    public static String denial(MinecraftServer server, HeraldorSafetyMode required) {
        return "safety_blocked required=" + required.serializedName()
                + " effective=" + effectiveMode(server).serializedName();
    }

    public static String statusLine(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyMode ceiling = HeraldorSafetyCeiling.current();
        return "heraldor_safety mode=" + data.effectiveMode(ceiling).serializedName()
                + " ceiling=" + ceiling.serializedName()
                + " generation=" + data.generation()
                + " nonce=" + data.nonce()
                + " incident=" + data.incidentId()
                + " writable=" + (data.schemaStatus().writable() ? 1 : 0);
    }

    /** Arms an explicit mode using the current one-time world nonce. */
    public static ActionOutcome arm(
            MinecraftServer server, HeraldorSafetyMode requested, UUID nonce) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(nonce, "nonce");
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyMode ceiling = HeraldorSafetyCeiling.current();
        HeraldorSafetyMode previous = data.effectiveMode(ceiling);
        if (requested == HeraldorSafetyMode.QUARANTINED) {
            return ActionOutcome.refused("invalid_mode", data, ceiling);
        }
        if (!ceiling.allows(requested)) {
            return ActionOutcome.refused("ceiling_refused", data, ceiling);
        }
        HeraldorSafetyData.TransitionResult transition = data.transition(requested, nonce);
        if (!transition.accepted()) {
            return ActionOutcome.refused(
                    transition.status().name().toLowerCase(java.util.Locale.ROOT), data, ceiling);
        }
        try {
            flushSafetyAuthority(server);
        } catch (RuntimeException persistenceFailure) {
            ZapeGRuntime.LOGGER.error(
                    "Failed to persist Heraldor safety arm; applying emergency quarantine",
                    persistenceFailure);
            data.emergencyQuarantine();
            try {
                flushSafetyAuthority(server);
            } catch (RuntimeException quarantineFailure) {
                ZapeGRuntime.LOGGER.error(
                        "Failed to persist fallback Heraldor quarantine", quarantineFailure);
            }
            cleanup(server, 1);
            return ActionOutcome.refused("persistence_failed", data, ceiling);
        }
        if (requested.ordinal() < previous.ordinal()) {
            CleanupCounts downshiftCleanup = cleanup(server, 0);
            if (downshiftCleanup.unresolved() == 0) {
                markEnforced(server, data.generation());
            }
        } else {
            markEnforced(server, data.generation());
        }
        return new ActionOutcome(
                true,
                transition.status().name().toLowerCase(java.util.Locale.ROOT),
                data.effectiveMode(ceiling),
                ceiling,
                data.generation(),
                data.nonce());
    }

    /**
     * Nonce-free emergency brake. The quarantine authority is synchronously flushed before the
     * first cancellation call, so a crash at that boundary restarts fail-closed.
     */
    public static StopOutcome emergencyStop(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        data.emergencyQuarantine();
        clearEnforced(server);
        int persistenceFailures = 0;
        try {
            flushSafetyAuthority(server);
        } catch (RuntimeException failure) {
            persistenceFailures = 1;
            ZapeGRuntime.LOGGER.error(
                    "Emergency quarantine could not be flushed before cleanup", failure);
        }
        CleanupCounts counts = cleanup(server, persistenceFailures);
        if (counts.unresolved() == 0) {
            markEnforced(server, data.generation());
        }
        return new StopOutcome(data.incidentId(), counts);
    }

    /** Repeats transient cleanup without changing mode, generation, nonce, or incident. */
    public static CleanupOutcome cleanup(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        CleanupCounts counts = cleanup(server, 0);
        if (counts.unresolved() == 0) {
            markEnforced(server, HeraldorSafetyData.get(server).generation());
        }
        return new CleanupOutcome(effectiveMode(server), counts);
    }

    /**
     * First-END-tick crash recovery. Any generation not observed in this process is sanitized
     * before it may continue below AUTO; unresolved cleanup is retried on every later END tick.
     */
    public static boolean enforce(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyMode ceiling = HeraldorSafetyCeiling.current();
        HeraldorSafetyData.TransitionResult ceilingReconciliation =
                data.reconcileCeiling(ceiling);
        if (ceilingReconciliation.status() == HeraldorSafetyData.TransitionStatus.APPLIED) {
            clearEnforced(server);
            try {
                // Destroy hidden authority durably before any cleanup or producer may run.
                flushSafetyAuthority(server);
            } catch (RuntimeException failure) {
                ZapeGRuntime.LOGGER.error(
                        "Ceiling-forced Heraldor quarantine could not be flushed", failure);
                cleanup(server, 1);
                return false;
            }
        }
        long generation = data.generation();
        synchronized (ENFORCED_GENERATIONS) {
            if (Objects.equals(ENFORCED_GENERATIONS.get(server), generation)) {
                return data.effectiveMode(ceiling) != HeraldorSafetyMode.QUARANTINED;
            }
        }
        HeraldorSafetyMode effective = data.effectiveMode(ceiling);
        if (effective != HeraldorSafetyMode.AUTO) {
            CleanupCounts counts = cleanup(server, 0);
            if (counts.unresolved() == 0) {
                markEnforced(server, generation);
            }
        } else {
            markEnforced(server, generation);
        }
        return effective != HeraldorSafetyMode.QUARANTINED;
    }

    private static void markEnforced(MinecraftServer server, long generation) {
        synchronized (ENFORCED_GENERATIONS) {
            ENFORCED_GENERATIONS.put(server, generation);
        }
    }

    private static void clearEnforced(MinecraftServer server) {
        synchronized (ENFORCED_GENERATIONS) {
            ENFORCED_GENERATIONS.remove(server);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server != null) {
            clearEnforced(server);
        }
    }

    private static void flushSafetyAuthority(MinecraftServer server) {
        server.overworld().getDataStorage().save();
    }

    private static CleanupCounts cleanup(MinecraftServer server, int priorUnresolved) {
        Counter unresolved = new Counter(priorUnresolved);
        int scenes = safely("scenes", unresolved,
                () -> SceneServerManager.cancelAll(CancelReason.OPERATOR));
        int timelines = safely("timelines", unresolved,
                () -> TimelineServerManager.cancelAll(server));
        int servants = safely("servants", unresolved,
                () -> ServantEncounterManager.cancelAll(
                        server, ServantEncounterManager.CloseReason.OPERATOR));
        NinthFormEncounterManager.AbortSummary bossSummary;
        try {
            bossSummary = NinthFormEncounterManager.abortAll(server);
            unresolved.value += bossSummary.unresolved();
        } catch (RuntimeException failure) {
            unresolved.value++;
            bossSummary = new NinthFormEncounterManager.AbortSummary(0, 0);
            ZapeGRuntime.LOGGER.error("Heraldor safety cleanup failed for bosses", failure);
        }
        safely("director queues", unresolved, () -> HeraldorDirector.clearForSafety(server));
        safely("quest sessions", unresolved, QuestServerEvents::clearForSafety);

        unresolved.value += safelyCountRemaining("scenes", unresolved, SceneServerManager::activeCount);
        unresolved.value += safelyCountRemaining(
                "timelines", unresolved, () -> TimelineServerManager.activeCount(server));
        unresolved.value += safelyCountRemaining(
                "servants", unresolved, () -> ServantEncounterManager.activeCount(server));

        try {
            server.overworld().getDataStorage().save();
        } catch (RuntimeException failure) {
            unresolved.value++;
            ZapeGRuntime.LOGGER.error("Heraldor cleanup state could not be flushed", failure);
        }
        return new CleanupCounts(
                scenes, timelines, servants, bossSummary.aborted(), unresolved.value);
    }

    private static int safely(String subsystem, Counter unresolved, IntSupplier operation) {
        try {
            return Math.max(0, operation.getAsInt());
        } catch (RuntimeException failure) {
            unresolved.value++;
            ZapeGRuntime.LOGGER.error(
                    "Heraldor safety cleanup failed for {}", subsystem, failure);
            return 0;
        }
    }

    private static int safelyCountRemaining(
            String subsystem, Counter unresolved, IntSupplier operation) {
        try {
            return Math.max(0, operation.getAsInt());
        } catch (RuntimeException failure) {
            unresolved.value++;
            ZapeGRuntime.LOGGER.error(
                    "Heraldor safety could not verify {} cleanup", subsystem, failure);
            return 0;
        }
    }

    private static final class Counter {
        private int value;

        private Counter(int value) {
            this.value = value;
        }
    }

    public record CleanupCounts(
            int scenes, int timelines, int servants, int bosses, int unresolved) {}

    public record ActionOutcome(
            boolean success,
            String reason,
            HeraldorSafetyMode mode,
            HeraldorSafetyMode ceiling,
            long generation,
            UUID nextNonce) {

        private static ActionOutcome refused(
                String reason, HeraldorSafetyData data, HeraldorSafetyMode ceiling) {
            return new ActionOutcome(
                    false,
                    reason,
                    data.effectiveMode(ceiling),
                    ceiling,
                    data.generation(),
                    data.nonce());
        }

        public String machineLine() {
            if (!success) {
                return "heraldor_safety refused reason=" + reason
                        + " mode=" + mode.serializedName()
                        + " ceiling=" + ceiling.serializedName()
                        + " generation=" + generation;
            }
            return "heraldor_safety armed mode=" + mode.serializedName()
                    + " ceiling=" + ceiling.serializedName()
                    + " generation=" + generation
                    + " nonce_rotated=1";
        }
    }

    public record StopOutcome(UUID incidentId, CleanupCounts counts) {
        public String machineLine() {
            return "heraldor_safety stopped mode=quarantined incident=" + incidentId
                    + countsSuffix(counts);
        }
    }

    public record CleanupOutcome(HeraldorSafetyMode mode, CleanupCounts counts) {
        public String machineLine() {
            return "heraldor_safety cleaned mode=" + mode.serializedName()
                    + countsSuffix(counts);
        }
    }

    private static String countsSuffix(CleanupCounts counts) {
        return " scenes=" + counts.scenes()
                + " timelines=" + counts.timelines()
                + " servants=" + counts.servants()
                + " bosses=" + counts.bosses()
                + " unresolved=" + counts.unresolved()
                + " evidence_preserved=1";
    }
}
