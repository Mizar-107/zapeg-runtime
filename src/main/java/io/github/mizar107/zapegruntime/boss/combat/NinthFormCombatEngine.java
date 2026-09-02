package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import io.github.mizar107.zapegruntime.boss.presentation.NinthFormSounds;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Server-only deterministic attack and damage executor. */
final class NinthFormCombatEngine {

    private static final double MAX_ARENA_INTERACTION_DISTANCE = 64.0D;

    private NinthFormCombatEngine() {}

    static void tick(NinthFormBoss boss, ServerLevel level) {
        if (!NinthFormLoadedFootprint.fullyLoaded(level, boss.loadedFootprint())) {
            boss.resetAttackToWindup();
            return;
        }
        Optional<NinthFormIdentity> installed = boss.encounterIdentity();
        if (installed.isEmpty()) {
            return;
        }
        NinthFormPhase phase = boss.combatPhase();
        // Terminal proof must win over target-loss suspension. A BANISHED
        // entity may tick after its target logs out, and SUSPENDED is not a
        // valid terminal signal.
        if (phase == NinthFormPhase.BANISHED) {
            if (!boss.defeatSignalEmitted()
                    && boss.emitCombatSignal(
                            NinthFormCombatSignal.Kind.DEFEATED,
                            NinthFormPhase.BANISHED)) {
                boss.markDefeatSignalEmitted();
            }
            return;
        }
        ServerPlayer target = level.getServer().getPlayerList()
                .getPlayer(installed.get().targetId());
        if (target == null
                || target.serverLevel() != level
                || !target.isAlive()
                || !NinthFormCombatGeometry.insideConfinement(
                        boss.position(), target.position())) {
            if (!boss.suspendedSignalEmitted()
                    && boss.emitCombatSignal(
                            NinthFormCombatSignal.Kind.SUSPENDED, boss.combatPhase())) {
                boss.setSuspendedSignalEmitted(true);
            }
            boss.resetAttackToWindup();
            return;
        }
        boss.setSuspendedSignalEmitted(false);
        applyConfinement(boss, level);

        NinthFormPhaseHandshake.Action phaseAction = NinthFormPhaseHandshake.next(
                phase, boss.brokenPointMask(), boss.phaseSignalEmitted());
        if (phaseAction == NinthFormPhaseHandshake.Action.TRANSITION_TO_INTERLUDE) {
            boss.resetAttackToWindup();
            if (!boss.transition(NinthFormPhase.FIRST, NinthFormPhase.INTERLUDE)) {
                return;
            }
            phase = NinthFormPhase.INTERLUDE;
            phaseAction = NinthFormPhaseHandshake.next(
                    phase, boss.brokenPointMask(), boss.phaseSignalEmitted());
        }
        if (phaseAction == NinthFormPhaseHandshake.Action.EMIT_FIRST_PHASE_PROOF) {
            if (boss.emitCombatSignal(
                            NinthFormCombatSignal.Kind.PHASE_COMPLETED,
                            NinthFormPhase.FIRST)) {
                boss.markPhaseSignalEmitted();
            }
            return;
        }
        if (phase != NinthFormPhase.FIRST && phase != NinthFormPhase.FINAL) {
            boss.setAttackState(boss.attackCycle(), "idle", 0);
            return;
        }
        if ((phase == NinthFormPhase.FIRST || phase == NinthFormPhase.FINAL)
                && boss.tickCount % 80 == 0) {
            level.playSound(
                    null,
                    boss.getX(),
                    boss.getY(),
                    boss.getZ(),
                    NinthFormSounds.BED.get(),
                    SoundSource.MUSIC,
                    0.55F,
                    1.0F);
        }
        driveAttack(boss, level, installed.get(), target, phase);
    }

    static boolean hurtPart(
            NinthFormBoss boss,
            NinthFormPartKind part,
            DamageSource source,
            float rawDamage) {
        if (!(boss.level() instanceof ServerLevel level)
                || !boss.hasSignalSink()
                || !encounterTargetWithinArena(boss, level)
                || !NinthFormLoadedFootprint.fullyLoaded(level, boss.loadedFootprint())) {
            return false;
        }
        Optional<ServerPlayer> attributed = NinthFormDamageAttribution.creditedPlayer(source);
        if (attributed.isEmpty() || !validAttacker(boss, attributed.get(), level)) {
            return false;
        }
        double weakFraction = part.weakPoint() ? boss.weakPointHealth(part) : 0.0D;
        NinthFormDamagePolicy.DamageDecision decision = NinthFormDamagePolicy.routePart(
                boss.combatPhase(),
                part,
                boss.brokenPointMask(),
                rawDamage,
                boss.getHealth(),
                boss.getMaxHealth(),
                weakFraction,
                boss.healthScale());
        return applyDecision(boss, level, attributed.get(), part, decision);
    }

    static boolean hurtParent(NinthFormBoss boss, DamageSource source, float rawDamage) {
        if (!(boss.level() instanceof ServerLevel level)
                || !boss.hasSignalSink()
                || !encounterTargetWithinArena(boss, level)
                || !NinthFormLoadedFootprint.fullyLoaded(level, boss.loadedFootprint())) {
            return false;
        }
        Optional<ServerPlayer> attributed = NinthFormDamageAttribution.creditedPlayer(source);
        if (attributed.isEmpty() || !validAttacker(boss, attributed.get(), level)) {
            return false;
        }
        NinthFormDamagePolicy.DamageDecision decision = NinthFormDamagePolicy.routeParent(
                boss.combatPhase(),
                boss.brokenPointMask(),
                rawDamage,
                boss.getHealth(),
                boss.getMaxHealth());
        return applyDecision(boss, level, attributed.get(), null, decision);
    }

    private static boolean applyDecision(
            NinthFormBoss boss,
            ServerLevel level,
            ServerPlayer attacker,
            NinthFormPartKind part,
            NinthFormDamagePolicy.DamageDecision decision) {
        if (decision.target() == NinthFormDamagePolicy.DamageTarget.IMMUNE) {
            return false;
        }
        boss.setLastHurtByPlayer(attacker);
        if (decision.target() == NinthFormDamagePolicy.DamageTarget.WEAK_POINT) {
            double maximum = NinthFormDamagePolicy.WEAK_POINT_BASE_HEALTH * boss.healthScale();
            double remaining = Math.max(
                    0.0D,
                    boss.weakPointHealth(part) - decision.appliedDamage() / maximum);
            boss.setWeakPointHealth(part, remaining);
            if (remaining == 0.0D) {
                level.playSound(
                        null,
                        boss.getX(),
                        boss.getY() + part.verticalOffset(),
                        boss.getZ(),
                        NinthFormSounds.WEAKPOINT_BREAK.get(),
                        SoundSource.HOSTILE,
                        2.0F,
                        0.55F);
                level.sendParticles(
                        ParticleTypes.SCRAPE,
                        boss.getX(),
                        boss.getY() + part.verticalOffset(),
                        boss.getZ(),
                        28,
                        1.2D,
                        1.0D,
                        1.2D,
                        0.08D);
            }
        } else {
            boss.applyParentDamage(decision.appliedDamage());
        }
        boss.showDamageFeedback();
        return true;
    }

    private static boolean validAttacker(
            NinthFormBoss boss, ServerPlayer player, ServerLevel level) {
        return player.serverLevel() == level
                && player.isAlive()
                && !player.isSpectator()
                && NinthFormCombatGeometry.insideConfinement(
                        boss.position(), player.position());
    }

    private static boolean encounterTargetWithinArena(
            NinthFormBoss boss, ServerLevel level) {
        Optional<NinthFormIdentity> installed = boss.encounterIdentity();
        if (installed.isEmpty()) {
            return false;
        }
        ServerPlayer target = level.getServer().getPlayerList()
                .getPlayer(installed.get().targetId());
        return target != null
                && target.serverLevel() == level
                && target.isAlive()
                && NinthFormCombatGeometry.insideConfinement(
                        boss.position(), target.position());
    }

    private static void driveAttack(
            NinthFormBoss boss,
            ServerLevel level,
            NinthFormIdentity identity,
            ServerPlayer target,
            NinthFormPhase phase) {
        NinthFormAttack attack = NinthFormAttack.parse(boss.attackId())
                .filter(candidate -> candidate.allowedIn(phase))
                .orElseGet(() -> {
                    NinthFormAttack selected = NinthFormAttackSelector.select(
                            identity.encounterId(), phase, boss.attackCycle(), boss.attackId());
                    boss.setAttackState(boss.attackCycle(), selected.serializedName(), 0);
                    return selected;
                });
        int tick = boss.attackTick();
        NinthFormAttack.AttackWindow window = attack.windowAt(tick);
        if (window == NinthFormAttack.AttackWindow.COMPLETE) {
            long nextCycle = boss.attackCycle() == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : boss.attackCycle() + 1L;
            NinthFormAttack next = NinthFormAttackSelector.select(
                    identity.encounterId(), phase, nextCycle, attack.serializedName());
            boss.setAttackState(nextCycle, next.serializedName(), 0);
            return;
        }
        if (tick == 0) {
            boss.lockAttackAnchor(target.position());
            level.playSound(
                    null,
                    boss.getX(),
                    boss.getY(),
                    boss.getZ(),
                    NinthFormSounds.TELEGRAPH.get(),
                    SoundSource.HOSTILE,
                    2.2F,
                    telegraphPitch(attack));
        }
        if (window == NinthFormAttack.AttackWindow.WINDUP) {
            trackTargetDuringWindup(boss, attack, target.position());
        } else {
            Optional<Float> lockedYaw = boss.attackYaw();
            if (lockedYaw.isEmpty()) {
                boss.resetAttackToWindup();
                return;
            }
            boss.applyAttackYaw(lockedYaw.get());
        }
        if (window == NinthFormAttack.AttackWindow.WINDUP && tick % 5 == 0) {
            telegraph(level, boss, attack);
        } else if (window == NinthFormAttack.AttackWindow.ACTIVE) {
            if (attack == NinthFormAttack.ANCHORFALL && boss.attackAnchor().isEmpty()) {
                boss.resetAttackToWindup();
                return;
            }
            resolveActive(level, boss, attack, attack.activeAge(tick));
        }
        boss.setAttackState(boss.attackCycle(), attack.serializedName(), tick + 1);
    }

    private static void applyConfinement(NinthFormBoss boss, ServerLevel level) {
        Vec3 origin = boss.position();
        double maximumSquared = MAX_ARENA_INTERACTION_DISTANCE
                * MAX_ARENA_INTERACTION_DISTANCE;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level
                    || !player.isAlive()
                    || player.isSpectator()
                    || horizontalDistanceSquared(player.position(), origin) > maximumSquared) {
                continue;
            }
            Vec3 impulse = NinthFormCombatGeometry.confinementImpulse(
                    origin, player.position());
            if (impulse.lengthSqr() > 0.0D) {
                player.push(impulse.x, impulse.y, impulse.z);
                player.hurtMarked = true;
            }
        }
    }

    private static void telegraph(
            ServerLevel level,
            NinthFormBoss boss,
            NinthFormAttack attack) {
        Vec3 origin = boss.position();
        float yaw = boss.attackYaw().orElse(boss.getYRot());
        ParticleOptions particle = switch (attack) {
            case KEEL_SWEEP -> ParticleTypes.SWEEP_ATTACK;
            case ANCHORFALL -> ParticleTypes.FALLING_WATER;
            case UNDERTOW -> ParticleTypes.BUBBLE;
            case DROWNED_BROADSIDE -> ParticleTypes.SMOKE;
            case WAKE_CHARGE -> ParticleTypes.SPLASH;
            case NINEFOLD_GAZE -> ParticleTypes.SOUL_FIRE_FLAME;
        };
        Vec3 point = attack == NinthFormAttack.ANCHORFALL
                ? boss.attackAnchor().orElse(origin)
                : origin.add(0.0D, 2.5D, 0.0D);
        level.sendParticles(particle, point.x, point.y, point.z, 8, 1.2D, 0.6D, 1.2D, 0.02D);
        outline(level, particle, origin, yaw, attack, boss.attackAnchor().orElse(origin));
    }

    private static void outline(
            ServerLevel level,
            ParticleOptions particle,
            Vec3 origin,
            float yaw,
            NinthFormAttack attack,
            Vec3 anchor) {
        switch (attack) {
            case KEEL_SWEEP -> ring(level, particle, origin, 16.0D, 24);
            case ANCHORFALL -> ring(level, particle, anchor, 5.0D, 16);
            case UNDERTOW -> ring(
                    level, particle, origin, NinthFormCombatGeometry.CONFINEMENT_RADIUS, 32);
            case DROWNED_BROADSIDE -> {
                scatterSlab(level, particle, origin, yaw, 12.0D);
                scatterSlab(level, particle, origin, yaw, -12.0D);
            }
            case WAKE_CHARGE -> {
                for (int step = 0; step <= 6; step++) {
                    double forward = step * 6.0D;
                    Vec3 left = worldOffset(origin, yaw, -(4.0D + forward * 0.22D), forward);
                    Vec3 right = worldOffset(origin, yaw, 4.0D + forward * 0.22D, forward);
                    burst(level, particle, left);
                    burst(level, particle, right);
                }
            }
            case NINEFOLD_GAZE -> {
                double reach = NinthFormCombatGeometry.CONFINEMENT_RADIUS;
                double half = Math.tan(Math.toRadians(11.0D)) * reach;
                burst(level, particle, worldOffset(origin, yaw, -half, reach));
                burst(level, particle, worldOffset(origin, yaw, half, reach));
                burst(level, particle, worldOffset(origin, yaw, 0.0D, reach));
            }
        }
    }

    private static void ring(
            ServerLevel level, ParticleOptions particle, Vec3 origin, double radius, int segments) {
        for (int index = 0; index < segments; index++) {
            double angle = index * (Math.PI * 2.0D) / segments;
            burst(
                    level,
                    particle,
                    origin.add(Math.cos(angle) * radius, 0.2D, Math.sin(angle) * radius));
        }
    }

    private static void scatterSlab(
            ServerLevel level, ParticleOptions particle, Vec3 origin, float yaw, double lateral) {
        for (int step = -2; step <= 2; step++) {
            burst(level, particle, worldOffset(origin, yaw, lateral, step * 5.0D));
        }
    }

    private static void burst(ServerLevel level, ParticleOptions particle, Vec3 point) {
        level.sendParticles(particle, point.x, point.y + 0.4D, point.z, 2, 0.12D, 0.08D, 0.12D, 0.0D);
    }

    private static Vec3 worldOffset(Vec3 origin, float yaw, double lateral, double forward) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return origin.add(lateral * cos - forward * sin, 0.0D, lateral * sin + forward * cos);
    }

    private static void resolveActive(
            ServerLevel level,
            NinthFormBoss boss,
            NinthFormAttack attack,
            int activeAge) {
        if (activeAge == 0) {
            level.playSound(
                    null,
                    boss.getX(),
                    boss.getY(),
                    boss.getZ(),
                    NinthFormSounds.IMPACT.get(),
                    SoundSource.HOSTILE,
                    1.8F,
                    0.65F + attack.ordinal() * 0.06F);
        }
        Vec3 origin = boss.position();
        float attackYaw = boss.attackYaw().orElseThrow();
        Vec3 anchorfall = attack == NinthFormAttack.ANCHORFALL
                ? boss.attackAnchor().orElse(origin)
                : origin;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level
                    || !player.isAlive()
                    || player.isSpectator()
                    || !NinthFormCombatGeometry.insideConfinement(origin, player.position())) {
                continue;
            }
            boolean hit = switch (attack) {
                case KEEL_SWEEP -> activeAge == 0
                        && NinthFormCombatGeometry.insideKeelSweep(origin, player.position());
                case ANCHORFALL -> activeAge == 0
                        && NinthFormCombatGeometry.insideAnchorfall(
                                anchorfall, player.position());
                case UNDERTOW -> (activeAge == 0 || activeAge == 10);
                case DROWNED_BROADSIDE -> activeAge == 0
                        && NinthFormCombatGeometry.insideBroadside(
                                origin, attackYaw, player.position());
                case WAKE_CHARGE -> (activeAge == 0 || activeAge == 8)
                        && NinthFormCombatGeometry.insideWakeCharge(
                                origin, attackYaw, player.position());
                case NINEFOLD_GAZE -> activeAge % 6 == 0
                        && NinthFormCombatGeometry.insideNinefoldGaze(
                                origin, attackYaw, player.position());
            };
            if (!hit) {
                if (attack == NinthFormAttack.UNDERTOW) {
                    applyUndertowPull(origin, player);
                }
                continue;
            }
            player.hurt(
                    level.damageSources().mobAttack(boss),
                    (float) (baseDamage(attack) * boss.damageScale()));
            applyAttackImpulse(origin, attackYaw, player, attack);
        }
    }

    private static void applyUndertowPull(Vec3 origin, ServerPlayer player) {
        Vec3 delta = origin.subtract(player.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() > 0.0001D) {
            Vec3 pull = horizontal.normalize().scale(0.08D);
            player.push(pull.x, 0.01D, pull.z);
            player.hurtMarked = true;
        }
    }

    private static void applyAttackImpulse(
            Vec3 origin,
            float yaw,
            ServerPlayer player,
            NinthFormAttack attack) {
        Vec3 impulse;
        if (attack == NinthFormAttack.UNDERTOW) {
            applyUndertowPull(origin, player);
            return;
        }
        if (attack == NinthFormAttack.WAKE_CHARGE) {
            double radians = Math.toRadians(yaw);
            impulse = new Vec3(-Math.sin(radians) * 0.75D, 0.18D, Math.cos(radians) * 0.75D);
        } else {
            Vec3 outward = player.position().subtract(origin);
            Vec3 horizontal = new Vec3(outward.x, 0.0D, outward.z);
            impulse = horizontal.lengthSqr() <= 0.0001D
                    ? new Vec3(0.0D, 0.25D, 0.0D)
                    : horizontal.normalize().scale(0.55D).add(0.0D, 0.2D, 0.0D);
        }
        player.push(impulse.x, impulse.y, impulse.z);
        player.hurtMarked = true;
    }

    private static double baseDamage(NinthFormAttack attack) {
        return switch (attack) {
            case KEEL_SWEEP -> 28.0D;
            case ANCHORFALL -> 36.0D;
            case UNDERTOW -> 10.0D;
            case DROWNED_BROADSIDE -> 32.0D;
            case WAKE_CHARGE -> 26.0D;
            case NINEFOLD_GAZE -> 12.0D;
        };
    }

    private static float telegraphPitch(NinthFormAttack attack) {
        return 0.55F + attack.ordinal() * 0.07F;
    }

    private static void trackTargetDuringWindup(
            NinthFormBoss boss, NinthFormAttack attack, Vec3 target) {
        float maximum = attack == NinthFormAttack.WAKE_CHARGE
                ? NinthFormCombatGeometry.MAX_WRECK_YAW_STEP
                : NinthFormCombatGeometry.MAX_WINDUP_YAW_STEP;
        float yaw = NinthFormCombatGeometry.boundedYawToward(
                boss.getYRot(),
                boss.position(),
                target,
                maximum);
        boss.setAttackYaw(yaw);
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }
}
