package io.github.mizar107.zapegruntime.boss.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NinthFormGatewayRegistryTest {

    @Test
    void duplicateServerBindingFailsClosedAndWrongGatewayCannotUnbind() {
        NinthFormGatewayRegistry.Bindings<Object> bindings =
                new NinthFormGatewayRegistry.Bindings<>();
        Object server = new Object();
        NinthFormEntityGateway first = new StubGateway();
        NinthFormEntityGateway second = new StubGateway();

        assertTrue(bindings.install(server, first));
        assertTrue(bindings.install(server, first));
        assertFalse(bindings.install(server, second));
        assertSame(first, bindings.current(server).orElseThrow());
        assertFalse(bindings.uninstall(server, second));
        assertTrue(bindings.available(server));
        assertTrue(bindings.uninstall(server, first));
        assertFalse(bindings.available(server));
        assertTrue(bindings.current(server).isEmpty());
        assertTrue(bindings.size() == 0);
    }

    @Test
    void separateLiveServersReceiveSeparateBoundedGateways() {
        NinthFormGatewayRegistry.Bindings<Object> bindings =
                new NinthFormGatewayRegistry.Bindings<>();
        Object firstServer = new Object();
        Object secondServer = new Object();
        NinthFormEntityGateway first = new StubGateway();
        NinthFormEntityGateway second = new StubGateway();
        assertTrue(bindings.install(firstServer, first));
        assertTrue(bindings.install(secondServer, second));
        assertSame(first, bindings.current(firstServer).orElseThrow());
        assertSame(second, bindings.current(secondServer).orElseThrow());
        assertTrue(bindings.size() == 2);
    }

    private static final class StubGateway implements NinthFormEntityGateway {
        @Override
        public SpawnResult spawnLoaded(SpawnRequest request) {
            return new SpawnResult(Status.APPLIED, Optional.of(request.entityId()), "spawned");
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
            return new ControlResult(Status.APPLIED, "transitioned");
        }

        @Override
        public ControlResult suspendLoaded(NinthFormIdentity identity, UUID entityId) {
            return new ControlResult(Status.APPLIED, "suspended");
        }

        @Override
        public ControlResult discardLoaded(NinthFormIdentity identity, UUID entityId) {
            return new ControlResult(Status.APPLIED, "discarded");
        }
    }
}
