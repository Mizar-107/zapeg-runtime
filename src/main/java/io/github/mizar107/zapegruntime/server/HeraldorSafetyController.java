package io.github.mizar107.zapegruntime.server;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import io.github.mizar107.zapegruntime.boss.encounter.NinthFormEncounterManager;
import io.github.mizar107.zapegruntime.director.HeraldorDirector;
import io.github.mizar107.zapegruntime.quest.QuestServerEvents;
import io.github.mizar107.zapegruntime.scene.CancelReason;
import io.github.mizar107.zapegruntime.servant.ServantEncounterManager;
import io.github.mizar107.zapegruntime.timeline.TimelineServerManager;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** One fail-closed authority query and one ordered emergency-cleanup coordinator. */
public final class HeraldorSafetyController {

    /** JVM-local proof that this exact generation was sanitized in this process. */
    private static final Map<MinecraftServer, Long> ENFORCED_GENERATIONS = new WeakHashMap<>();
    /** Generations revoked before a transition barrier is attempted in this JVM. */
    private static final Map<MinecraftServer, Long> REVOKED_GENERATIONS = new WeakHashMap<>();
    /** A new JVM must consume its one startup latch before persisted authority is considered. */
    private static final Map<MinecraftServer, Boolean> STARTUP_LATCHES = new WeakHashMap<>();
    /** Last disk inspection; refreshed by every safety enforcement tick. */
    private static final Map<MinecraftServer, HeraldorSafetyFuse.Inspection> AUTHORITY_STATES =
            new WeakHashMap<>();

    private HeraldorSafetyController() {}

    /**
     * Effective permission requires the consumed boot latch plus all three authorities: current
     * SavedData, an exact persistent mirror, and this JVM's zero-active cleanup certificate.
     */
    public static HeraldorSafetyMode effectiveMode(MinecraftServer server) {
        if (server == null) {
            return HeraldorSafetyMode.QUARANTINED;
        }
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyFuse.Inspection authority = authorityState(server);
        HeraldorSafetyMode configured =
                data.effectiveMode(HeraldorSafetyCeiling.current());
        return authorizedMode(
                configured,
                authority.matches(data),
                isEnforced(server, data.generation()),
                isRevoked(server, data.generation()),
                startupLatchConsumed(server));
    }

    static HeraldorSafetyMode authorizedMode(
            HeraldorSafetyMode configured,
            boolean authorityMatches,
            boolean cleanupCertified,
            boolean generationRevoked,
            boolean startupLatched) {
        Objects.requireNonNull(configured, "configured");
        return configured == HeraldorSafetyMode.QUARANTINED
                        || !authorityMatches
                        || !cleanupCertified
                        || generationRevoked
                        || !startupLatched
                ? HeraldorSafetyMode.QUARANTINED
                : configured;
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
        HeraldorSafetyFuse.Inspection authority = authorityState(server);
        boolean writableAuthority = data.schemaStatus().writable()
                && authority.matches(data)
                && isEnforced(server, data.generation())
                && !isRevoked(server, data.generation())
                && startupLatchConsumed(server);
        return formatStatus(
                effectiveMode(server),
                ceiling,
                data.generation(),
                data.nonce(),
                data.incidentId(),
                writableAuthority);
    }

    static String formatStatus(
            HeraldorSafetyMode mode,
            HeraldorSafetyMode ceiling,
            long generation,
            UUID nonce,
            UUID incidentId,
            boolean writable) {
        return "heraldor_safety mode=" + mode.serializedName()
                + " ceiling=" + ceiling.serializedName()
                + " generation=" + generation
                + " nonce=" + nonce
                + " incident=" + incidentId
                + " writable=" + (writable ? 1 : 0);
    }

    /** Arms an explicit mode using the current one-time world nonce. */
    public static ActionOutcome arm(
            MinecraftServer server, HeraldorSafetyMode requested, UUID nonce) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(nonce, "nonce");
        requireServerThread(server);
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyMode ceiling = HeraldorSafetyCeiling.current();
        if (!startupLatchConsumed(server)) {
            return ActionOutcome.refused("startup_not_enforced", data, ceiling, server);
        }
        if (requested == HeraldorSafetyMode.QUARANTINED) {
            return ActionOutcome.refused("invalid_mode", data, ceiling, server);
        }
        if (!ceiling.allows(requested)) {
            return ActionOutcome.refused("ceiling_refused", data, ceiling, server);
        }

        HeraldorSafetyData.TransitionStatus preview = data.previewTransition(requested, nonce);
        if (preview != HeraldorSafetyData.TransitionStatus.APPLIED
                && preview != HeraldorSafetyData.TransitionStatus.DUPLICATE) {
            return ActionOutcome.refused(
                    preview.name().toLowerCase(Locale.ROOT), data, ceiling, server);
        }

        boolean duplicate = preview == HeraldorSafetyData.TransitionStatus.DUPLICATE;
        long priorGeneration = data.generation();
        if (duplicate && !isEnforced(server, priorGeneration)) {
            return ActionOutcome.refused(
                    "duplicate_cleanup_uncertified", data, ceiling, server);
        }
        HeraldorSafetyMode previousConfigured = data.effectiveMode(ceiling);
        boolean sourceWasAuthorized = effectiveMode(server) != HeraldorSafetyMode.QUARANTINED;
        boolean barrierInstalled = false;
        if (!duplicate) {
            // Revoke the source generation in memory before touching the filesystem. Even if an
            // armed mirror cannot be replaced, neither this tick nor a later enforce may restore
            // the old authority automatically.
            if (shouldLatchRevocation(data.configuredMode())) {
                latchRevocation(server, priorGeneration);
            }
            clearEnforced(server);
            if (sourceWasAuthorized) {
                try {
                    rememberAuthority(server, authorityStore(server).install(
                            data.quarantineBarrierSnapshot()));
                    barrierInstalled = true;
                } catch (IOException | RuntimeException failure) {
                    rememberAuthority(
                            server, HeraldorSafetyFuse.Inspection.unsafe("barrier_install_failed"));
                    ZapeGRuntime.LOGGER.error(
                            "Failed to install Heraldor arm transition barrier", failure);
                    return ActionOutcome.refused(
                            "barrier_persistence_failed", data, ceiling, server);
                }
            }

            // Certificates are deliberately fresh for every real arm/upshift/downshift. A
            // duplicate may reuse one, but can never create it.
            CleanupCounts prerequisite = cleanup(server, 0);
            if (prerequisite.unresolved() != 0) {
                clearEnforced(server);
                return ActionOutcome.refused("cleanup_unresolved", data, ceiling, server);
            }
        }

        HeraldorSafetyData.TransitionResult transition = data.transition(requested, nonce);
        if (!transition.accepted()) {
            // Preview and mutation are on the same server thread; reaching this branch means an
            // internal invariant failed. A previously installed barrier remains fail-closed.
            clearEnforced(server);
            return ActionOutcome.refused(
                    "transition_invariant_failed", data, ceiling, server);
        }
        if (transition.status() == HeraldorSafetyData.TransitionStatus.APPLIED) {
            clearEnforced(server);
        }

        try {
            HeraldorSafetyPersistence.flushAndVerify(server, data);
        } catch (RuntimeException persistenceFailure) {
            clearEnforced(server);
            if (!barrierInstalled) {
                rememberAuthority(
                        server, HeraldorSafetyFuse.Inspection.unsafe("saved_data_unverified"));
            }
            ZapeGRuntime.LOGGER.error(
                    "Failed to persist and verify Heraldor safety arm", persistenceFailure);
            return ActionOutcome.refused("persistence_failed", data, ceiling, server);
        }

        if (transition.status() == HeraldorSafetyData.TransitionStatus.APPLIED) {
            if (requested.ordinal() < previousConfigured.ordinal()) {
                CleanupCounts downshift = cleanup(server, 0);
                if (downshift.unresolved() != 0) {
                    clearEnforced(server);
                    return ActionOutcome.refused("cleanup_unresolved", data, ceiling, server);
                }
            }
            // Every transition proved a fresh zero-active predecessor above. A downshift also
            // proves its post-transition generation here before certification.
            markEnforced(server, data.generation());
        }

        try {
            rememberAuthority(server, authorityStore(server).install(data));
        } catch (IOException | RuntimeException authorityFailure) {
            rememberAuthority(
                    server, HeraldorSafetyFuse.Inspection.unsafe("authority_install_failed"));
            ZapeGRuntime.LOGGER.error(
                    "Failed to install verified Heraldor arm authority", authorityFailure);
            // Keep the cleanup certificate: an exact duplicate may retry only the final atomic
            // install, but effectiveMode remains quarantined until the mirror matches.
            return ActionOutcome.refused("authority_install_failed", data, ceiling, server);
        }

        return new ActionOutcome(
                true,
                transition.status().name().toLowerCase(Locale.ROOT),
                effectiveMode(server),
                ceiling,
                data.generation(),
                data.nonce());
    }

    /**
     * Nonce-free emergency brake. A quarantine barrier is atomically installed before SavedData
     * mutation or cleanup, so every successfully crossed crash boundary restarts fail-closed.
     */
    public static StopOutcome emergencyStop(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        boolean barrierPersisted = false;
        boolean finalAuthorityPersisted = false;
        boolean savedDataPersisted = false;
        if (shouldLatchRevocation(data.configuredMode())) {
            latchRevocation(server, data.generation());
        }
        clearEnforced(server);
        int persistenceFailures = 0;
        try {
            rememberAuthority(server, authorityStore(server).install(
                    data.quarantineBarrierSnapshot()));
            barrierPersisted = true;
        } catch (IOException | RuntimeException barrierFailure) {
            persistenceFailures++;
            rememberAuthority(
                    server, HeraldorSafetyFuse.Inspection.unsafe("stop_barrier_failed"));
            ZapeGRuntime.LOGGER.error(
                    "Emergency quarantine barrier could not be installed", barrierFailure);
        }

        data.emergencyQuarantine();
        try {
            rememberAuthority(server, authorityStore(server).install(data));
            finalAuthorityPersisted = true;
        } catch (IOException | RuntimeException authorityFailure) {
            persistenceFailures++;
            rememberAuthority(
                    server, HeraldorSafetyFuse.Inspection.unsafe("stop_authority_failed"));
            ZapeGRuntime.LOGGER.error(
                    "Emergency quarantine authority could not be installed", authorityFailure);
        }
        try {
            HeraldorSafetyPersistence.flushAndVerify(server, data);
            savedDataPersisted = true;
        } catch (RuntimeException failure) {
            persistenceFailures++;
            ZapeGRuntime.LOGGER.error(
                    "Emergency quarantine SavedData could not be verified", failure);
        }

        CleanupCounts counts = cleanup(server, persistenceFailures);
        if (counts.unresolved() == 0) {
            markEnforced(server, data.generation());
        }
        boolean durableRevocation = durableRevocationProven(
                barrierPersisted, finalAuthorityPersisted, savedDataPersisted);
        if (durableRevocation) {
            clearRevocation(server, data.generation());
        }
        return new StopOutcome(data.incidentId(), counts, durableRevocation);
    }

    /** Repeats transient cleanup without changing mode, generation, nonce, or incident. */
    public static CleanupOutcome cleanup(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        CleanupCounts counts = cleanup(server, 0);
        if (counts.unresolved() == 0) {
            markEnforced(server, HeraldorSafetyData.get(server).generation());
        } else {
            clearEnforced(server);
        }
        return new CleanupOutcome(effectiveMode(server), counts);
    }

    /**
     * Startup/event-order barrier. A new JVM demotes every persisted armed mode; only a later
     * explicit arm may reopen it after startup has sanitized all transient authorities.
     */
    public static boolean enforce(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        HeraldorSafetyData data = HeraldorSafetyData.get(server);
        HeraldorSafetyMode ceiling = HeraldorSafetyCeiling.current();
        HeraldorSafetyFuse.Inspection diskAuthority = refreshAuthority(server);
        boolean firstEnforcement = consumeStartupLatch(server);
        boolean runtimeRevocation = isRevoked(server, data.generation());
        boolean bootDemotion = startupMustQuarantine(
                firstEnforcement, data.configuredMode(), runtimeRevocation);
        if (bootDemotion && data.schemaStatus().writable()) {
            latchRevocation(server, data.generation());
            data.emergencyQuarantine();
        }
        HeraldorSafetyData.TransitionResult ceilingReconciliation =
                data.reconcileCeiling(ceiling);

        if (diskAuthority.matches(data)
                && !bootDemotion
                && ceilingReconciliation.status()
                        != HeraldorSafetyData.TransitionStatus.APPLIED
                && isEnforced(server, data.generation())) {
            return effectiveMode(server) != HeraldorSafetyMode.QUARANTINED;
        }

        clearEnforced(server);
        int persistenceFailures = 0;
        boolean mirrorMismatch = !diskAuthority.matches(data);
        if (bootDemotion
                || ceilingReconciliation.status() == HeraldorSafetyData.TransitionStatus.APPLIED
                || mirrorMismatch) {
            // Missing/corrupt/mismatched authority never heals into an armed mode. Destroy hidden
            // authority and require a fresh explicit arm.
            if (data.schemaStatus().writable()
                    && data.configuredMode() != HeraldorSafetyMode.QUARANTINED) {
                data.emergencyQuarantine();
            }
            try {
                rememberAuthority(server, authorityStore(server).install(data));
            } catch (IOException | RuntimeException authorityFailure) {
                persistenceFailures++;
                rememberAuthority(
                        server, HeraldorSafetyFuse.Inspection.unsafe("startup_authority_failed"));
                ZapeGRuntime.LOGGER.error(
                        "Fail-closed Heraldor startup authority could not be installed",
                        authorityFailure);
            }
        }

        if (data.schemaStatus().writable()) {
            try {
                HeraldorSafetyPersistence.flushAndVerify(server, data);
            } catch (RuntimeException persistenceFailure) {
                persistenceFailures++;
                rememberAuthority(
                        server, HeraldorSafetyFuse.Inspection.unsafe("startup_saved_data_failed"));
                ZapeGRuntime.LOGGER.error(
                        "Heraldor startup safety SavedData could not be verified",
                        persistenceFailure);
            }
        }

        CleanupCounts counts = cleanup(server, persistenceFailures);
        if (counts.unresolved() == 0) {
            markEnforced(server, data.generation());
        }
        return effectiveMode(server) != HeraldorSafetyMode.QUARANTINED;
    }

    static boolean startupMustQuarantine(
            boolean firstEnforcement,
            HeraldorSafetyMode configuredMode,
            boolean generationRevoked) {
        Objects.requireNonNull(configuredMode, "configuredMode");
        return configuredMode != HeraldorSafetyMode.QUARANTINED
                && (firstEnforcement || generationRevoked);
    }

    static boolean shouldLatchRevocation(HeraldorSafetyMode configuredMode) {
        return Objects.requireNonNull(configuredMode, "configuredMode")
                != HeraldorSafetyMode.QUARANTINED;
    }

    static boolean durableRevocationProven(
            boolean barrierPersisted,
            boolean finalAuthorityPersisted,
            boolean savedDataPersisted) {
        return barrierPersisted
                || finalAuthorityPersisted
                || savedDataPersisted;
    }

    private static boolean isEnforced(MinecraftServer server, long generation) {
        synchronized (ENFORCED_GENERATIONS) {
            return Objects.equals(ENFORCED_GENERATIONS.get(server), generation);
        }
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

    private static boolean isRevoked(MinecraftServer server, long generation) {
        synchronized (REVOKED_GENERATIONS) {
            return Objects.equals(REVOKED_GENERATIONS.get(server), generation);
        }
    }

    private static void latchRevocation(MinecraftServer server, long generation) {
        synchronized (REVOKED_GENERATIONS) {
            REVOKED_GENERATIONS.put(server, generation);
        }
    }

    private static void clearRevocation(MinecraftServer server, long generation) {
        synchronized (REVOKED_GENERATIONS) {
            if (Objects.equals(REVOKED_GENERATIONS.get(server), generation)) {
                REVOKED_GENERATIONS.remove(server);
            }
        }
    }

    /** Returns true exactly once for each server instance/JVM lifetime. */
    private static boolean consumeStartupLatch(MinecraftServer server) {
        synchronized (STARTUP_LATCHES) {
            return STARTUP_LATCHES.put(server, Boolean.TRUE) == null;
        }
    }

    private static boolean startupLatchConsumed(MinecraftServer server) {
        synchronized (STARTUP_LATCHES) {
            return STARTUP_LATCHES.containsKey(server);
        }
    }

    private static HeraldorSafetyFuse authorityStore(MinecraftServer server) {
        return new HeraldorSafetyFuse(server.getWorldPath(LevelResource.ROOT));
    }

    private static HeraldorSafetyFuse.Inspection authorityState(MinecraftServer server) {
        synchronized (AUTHORITY_STATES) {
            HeraldorSafetyFuse.Inspection known = AUTHORITY_STATES.get(server);
            if (known != null) {
                return known;
            }
        }
        return refreshAuthority(server);
    }

    private static HeraldorSafetyFuse.Inspection refreshAuthority(MinecraftServer server) {
        HeraldorSafetyFuse.Inspection inspected = authorityStore(server).inspect();
        rememberAuthority(server, inspected);
        return inspected;
    }

    private static void rememberAuthority(
            MinecraftServer server, HeraldorSafetyFuse.Inspection inspection) {
        synchronized (AUTHORITY_STATES) {
            AUTHORITY_STATES.put(server, inspection);
        }
    }

    public static void forget(MinecraftServer server) {
        if (server == null) {
            return;
        }
        clearEnforced(server);
        synchronized (AUTHORITY_STATES) {
            AUTHORITY_STATES.remove(server);
        }
        synchronized (REVOKED_GENERATIONS) {
            REVOKED_GENERATIONS.remove(server);
        }
        synchronized (STARTUP_LATCHES) {
            STARTUP_LATCHES.remove(server);
        }
    }

    private static CleanupCounts cleanup(MinecraftServer server, int priorUnresolved) {
        Counter unresolved = new Counter(priorUnresolved);
        int scenes = safely(
                "scenes", unresolved, () -> SceneServerManager.cancelAll(CancelReason.OPERATOR));
        int timelines = safely(
                "timelines", unresolved, () -> TimelineServerManager.cancelAll(server));
        int closedServants = safely(
                "servant records",
                unresolved,
                () -> ServantEncounterManager.cancelAll(
                        server, ServantEncounterManager.CloseReason.OPERATOR));
        int sweptServants = safely(
                "loaded servant sweep", unresolved, () -> ServantEncounterManager.discardAllLoaded(server));
        int servants = saturatedAdd(closedServants, sweptServants);

        NinthFormEncounterManager.AbortSummary bossSummary;
        try {
            bossSummary = NinthFormEncounterManager.abortAll(server);
            unresolved.value = saturatedAdd(unresolved.value, bossSummary.unresolved());
        } catch (RuntimeException failure) {
            unresolved.value = saturatedAdd(unresolved.value, 1);
            bossSummary = new NinthFormEncounterManager.AbortSummary(0, 0);
            ZapeGRuntime.LOGGER.error("Heraldor safety cleanup failed for bosses", failure);
        }
        safely("director queues", unresolved, () -> HeraldorDirector.clearForSafety(server));
        safely("quest sessions", unresolved, QuestServerEvents::clearForSafety);

        unresolved.value = saturatedAdd(
                unresolved.value,
                safelyCountRemaining("scenes", SceneServerManager::activeCount));
        unresolved.value = saturatedAdd(
                unresolved.value,
                safelyCountRemaining(
                        "timelines", () -> TimelineServerManager.activeCount(server)));
        unresolved.value = saturatedAdd(
                unresolved.value,
                safelyCountRemaining(
                        "servant records", () -> ServantEncounterManager.activeCount(server)));
        unresolved.value = saturatedAdd(
                unresolved.value,
                safelyCountRemaining(
                        "loaded servants",
                        () -> ServantEncounterManager.loadedEntityCount(server)));

        // Best-effort subsystem save. Its IOException is swallowed by vanilla, so it is not used
        // as a safety certificate. Every new JVM sanitizes all modes again before admission.
        try {
            server.overworld().getDataStorage().save();
        } catch (RuntimeException unexpectedSaveFailure) {
            unresolved.value = saturatedAdd(unresolved.value, 1);
            ZapeGRuntime.LOGGER.error(
                    "Heraldor cleanup state save raised an unexpected failure",
                    unexpectedSaveFailure);
        }
        return new CleanupCounts(
                scenes, timelines, servants, bossSummary.aborted(), unresolved.value);
    }

    private static int safely(String subsystem, Counter unresolved, IntSupplier operation) {
        try {
            return Math.max(0, operation.getAsInt());
        } catch (RuntimeException failure) {
            unresolved.value = saturatedAdd(unresolved.value, 1);
            ZapeGRuntime.LOGGER.error(
                    "Heraldor safety cleanup failed for {}", subsystem, failure);
            return 0;
        }
    }

    private static int safelyCountRemaining(String subsystem, IntSupplier operation) {
        try {
            return Math.max(0, operation.getAsInt());
        } catch (RuntimeException failure) {
            ZapeGRuntime.LOGGER.error(
                    "Heraldor safety could not verify {} cleanup", subsystem, failure);
            // A failed verification is itself one unresolved item. Returning it keeps the
            // caller's single saturated addition monotonic and prevents a cleanup certificate.
            return 1;
        }
    }

    private static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, sum);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Heraldor safety mutation requires the server thread");
        }
    }

    private static final class Counter {
        private int value;

        private Counter(int value) {
            this.value = Math.max(0, value);
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
                String reason,
                HeraldorSafetyData data,
                HeraldorSafetyMode ceiling,
                MinecraftServer server) {
            return new ActionOutcome(
                    false,
                    reason,
                    HeraldorSafetyController.effectiveMode(server),
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

    public record StopOutcome(UUID incidentId, CleanupCounts counts, boolean durableRevocation) {
        public boolean success() {
            return durableRevocation && counts.unresolved() == 0;
        }

        public String machineLine() {
            String prefix;
            if (!durableRevocation) {
                prefix = "heraldor_safety stop_failed reason=persistence_failed";
            } else if (counts.unresolved() != 0) {
                prefix = "heraldor_safety stop_failed reason=cleanup_unresolved";
            } else {
                prefix = "heraldor_safety stopped";
            }
            return prefix + " mode=quarantined incident=" + incidentId
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
