package io.github.mizar107.zapegruntime.boss.encounter;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * One typed integration point; the encounter package never touches entity classes.
 *
 * <p>The combat module binds one server-scoped gateway during server start,
 * attaches its signal sink to the bounded end-tick inbox, routes callbacks
 * through {@code NinthFormEncounterManager#onCombatSignal}, validates every
 * entity join through {@link NinthFormEncounterManager#acceptsEntity}, and
 * unbinds the same gateway after server stop. Missing or duplicate bindings
 * fail closed.</p>
 */
public final class NinthFormGatewayRegistry {

    public static final int MAX_SERVER_BINDINGS = 8;
    private static final NinthFormEntityGateway UNAVAILABLE = new UnavailableGateway();
    private static final Bindings<MinecraftServer> BINDINGS =
            new Bindings<>(MAX_SERVER_BINDINGS);

    private NinthFormGatewayRegistry() {}

    /** Installs one combat implementation for one running server. */
    public static synchronized boolean install(
            MinecraftServer server, NinthFormEntityGateway gateway) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(gateway, "gateway");
        return BINDINGS.install(server, gateway);
    }

    public static synchronized boolean uninstall(
            MinecraftServer server, NinthFormEntityGateway gateway) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(gateway, "gateway");
        return BINDINGS.uninstall(server, gateway);
    }

    public static synchronized boolean available(MinecraftServer server) {
        return BINDINGS.available(server);
    }

    static synchronized NinthFormEntityGateway current(MinecraftServer server) {
        return BINDINGS.current(server).orElse(UNAVAILABLE);
    }

    /** Pure weak, one-value-per-owner binding table used by executable tests. */
    static final class Bindings<K> {
        private final int capacity;
        private final Map<K, NinthFormEntityGateway> values = new WeakHashMap<>();

        Bindings() {
            this(MAX_SERVER_BINDINGS);
        }

        Bindings(int capacity) {
            if (capacity < 1) {
                throw new IllegalArgumentException("binding capacity must be positive");
            }
            this.capacity = capacity;
        }

        synchronized boolean install(K owner, NinthFormEntityGateway gateway) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(gateway, "gateway");
            NinthFormEntityGateway current = values.get(owner);
            if (current != null && current != gateway) {
                return false;
            }
            if (current == null && values.size() >= capacity) {
                return false;
            }
            values.put(owner, gateway);
            return true;
        }

        synchronized boolean uninstall(K owner, NinthFormEntityGateway gateway) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(gateway, "gateway");
            return values.remove(owner, gateway);
        }

        synchronized boolean available(K owner) {
            return values.containsKey(owner);
        }

        synchronized Optional<NinthFormEntityGateway> current(K owner) {
            return Optional.ofNullable(values.get(owner));
        }

        synchronized int size() {
            return values.size();
        }
    }

    private static final class UnavailableGateway implements NinthFormEntityGateway {
        @Override
        public SpawnResult spawnLoaded(SpawnRequest request) {
            return new SpawnResult(Status.FAILED, Optional.empty(), "combat gateway is not installed");
        }

        @Override
        public Optional<NinthFormCombatSnapshot> observeLoaded(
                NinthFormIdentity identity, UUID entityId) {
            return Optional.empty();
        }

        @Override
        public ControlResult transitionLoaded(
                NinthFormIdentity identity,
                UUID entityId,
                NinthFormPhase expected,
                NinthFormPhase next) {
            return new ControlResult(Status.FAILED, "combat gateway is not installed");
        }

        @Override
        public ControlResult suspendLoaded(NinthFormIdentity identity, UUID entityId) {
            return new ControlResult(Status.FAILED, "combat gateway is not installed");
        }

        @Override
        public ControlResult discardLoaded(NinthFormIdentity identity, UUID entityId) {
            return new ControlResult(Status.FAILED, "combat gateway is not installed");
        }
    }
}
