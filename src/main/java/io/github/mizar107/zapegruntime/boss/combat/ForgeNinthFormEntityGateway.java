package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Loaded-only Forge implementation of the encounter-facing entity gateway. */
public final class ForgeNinthFormEntityGateway implements NinthFormEntityGateway {

    private final MinecraftServer server;
    private final Consumer<NinthFormCombatSignal> signalSink;

    public ForgeNinthFormEntityGateway(
            MinecraftServer server, Consumer<NinthFormCombatSignal> signalSink) {
        this.server = Objects.requireNonNull(server, "server");
        this.signalSink = Objects.requireNonNull(signalSink, "signalSink");
    }

    @Override
    public SpawnResult spawnLoaded(SpawnRequest request) {
        Objects.requireNonNull(request, "request");
        if (!server.isSameThread()) {
            return spawnRefused(Status.FAILED, "server_thread_required");
        }
        Optional<ServerLevel> resolved = resolveDimension(request.dimensionId());
        if (resolved.isEmpty()) {
            return spawnRefused(Status.NOT_LOADED, "dimension_not_loaded");
        }
        ServerLevel level = resolved.get();
        if (!NinthFormLoadedFootprint.fullyLoaded(
                level, NinthFormBoss.footprintAt(request.x(), request.y(), request.z()))) {
            return spawnRefused(Status.NOT_LOADED, "spawn_footprint_not_loaded");
        }

        Entity occupied = level.getEntity(request.entityId());
        if (occupied != null) {
            if (occupied instanceof NinthFormBoss existing
                    && existing.identityMatches(request.identity(), request.entityId())) {
                return new SpawnResult(
                        Status.APPLIED, Optional.of(request.entityId()), "already_spawned");
            }
            return spawnRefused(Status.IDENTITY_MISMATCH, "entity_uuid_is_occupied");
        }

        NinthFormBoss boss;
        try {
            boss = NinthFormEntities.NINTH_FORM.get().create(level);
            if (boss == null) {
                return spawnRefused(Status.FAILED, "entity_factory_returned_null");
            }
            boss.setUUID(request.entityId());
            boss.moveTo(request.x(), request.y(), request.z(), 0.0F, 0.0F);
            boss.attachSignalSink(signalSink);
            boss.configure(request);
        } catch (RuntimeException invalid) {
            return spawnRefused(Status.FAILED, "entity_configuration_failed");
        }
        if (!level.addFreshEntity(boss)) {
            boss.discard();
            return spawnRefused(Status.FAILED, "entity_addition_failed");
        }
        return new SpawnResult(Status.APPLIED, Optional.of(boss.getUUID()), "spawned_loaded");
    }

    @Override
    public Optional<NinthFormCombatSnapshot> observeLoaded(
            NinthFormIdentity identity, UUID entityId) {
        Objects.requireNonNull(identity, "identity");
        identity.validateEntityId(entityId);
        if (!server.isSameThread()) {
            return Optional.empty();
        }
        Located located = locate(entityId);
        if (located.status() != Status.APPLIED
                || !located.boss().identityMatches(identity, entityId)
                || !NinthFormLoadedFootprint.fullyLoaded(
                        located.level(), located.boss().loadedFootprint())) {
            return Optional.empty();
        }
        located.boss().attachSignalSink(signalSink);
        return Optional.of(located.boss().snapshot(
                Math.max(0L, located.level().getGameTime())));
    }

    @Override
    public ControlResult transitionLoaded(
            NinthFormIdentity identity,
            UUID entityId,
            NinthFormPhase expected,
            NinthFormPhase next) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        Checked checked = checkedLoaded(identity, entityId);
        if (checked.status() != Status.APPLIED) {
            return new ControlResult(checked.status(), checked.detail());
        }
        if (!checked.boss().transition(expected, next)) {
            return new ControlResult(Status.STATE_MISMATCH, "phase_transition_rejected");
        }
        return new ControlResult(Status.APPLIED, "phase_transitioned");
    }

    @Override
    public ControlResult suspendLoaded(NinthFormIdentity identity, UUID entityId) {
        Checked checked = checkedLoaded(identity, entityId);
        if (checked.status() != Status.APPLIED) {
            return new ControlResult(checked.status(), checked.detail());
        }
        checked.boss().discard();
        return new ControlResult(Status.APPLIED, "entity_suspended");
    }

    @Override
    public ControlResult discardLoaded(NinthFormIdentity identity, UUID entityId) {
        Checked checked = checkedLoaded(identity, entityId);
        if (checked.status() != Status.APPLIED) {
            return new ControlResult(checked.status(), checked.detail());
        }
        checked.boss().discard();
        return new ControlResult(Status.APPLIED, "entity_discarded");
    }

    private Checked checkedLoaded(NinthFormIdentity identity, UUID entityId) {
        Objects.requireNonNull(identity, "identity");
        identity.validateEntityId(entityId);
        if (!server.isSameThread()) {
            return new Checked(Status.FAILED, null, "server_thread_required");
        }
        Located located = locate(entityId);
        if (located.status() != Status.APPLIED) {
            return new Checked(located.status(), null, located.detail());
        }
        if (!located.boss().identityMatches(identity, entityId)) {
            return new Checked(Status.IDENTITY_MISMATCH, null, "encounter_identity_mismatch");
        }
        if (!NinthFormLoadedFootprint.fullyLoaded(
                located.level(), located.boss().loadedFootprint())) {
            return new Checked(Status.NOT_LOADED, null, "entity_footprint_not_loaded");
        }
        located.boss().attachSignalSink(signalSink);
        return new Checked(Status.APPLIED, located.boss(), "loaded");
    }

    private Located locate(UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            if (entity instanceof NinthFormBoss boss) {
                return new Located(Status.APPLIED, level, boss, "loaded");
            }
            return new Located(Status.IDENTITY_MISMATCH, level, null, "entity_type_mismatch");
        }
        return new Located(Status.NOT_FOUND, null, null, "entity_not_found");
    }

    private Optional<ServerLevel> resolveDimension(String encoded) {
        ResourceLocation id = ResourceLocation.tryParse(encoded);
        if (id == null || !id.toString().equals(encoded)) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return Optional.ofNullable(server.getLevel(key));
    }

    private static SpawnResult spawnRefused(Status status, String detail) {
        return new SpawnResult(status, Optional.empty(), detail);
    }

    private record Located(
            Status status,
            ServerLevel level,
            NinthFormBoss boss,
            String detail) {}

    private record Checked(Status status, NinthFormBoss boss, String detail) {}
}
