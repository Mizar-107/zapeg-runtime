package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.server.HeraldorSafetyController;
import io.github.mizar107.zapegruntime.server.HeraldorSafetyMode;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A real server-side Servant with a single immutable player target.
 *
 * <p>Vanilla targeting goals are intentionally not registered: a Servant may
 * neither retaliate against, acquire, damage, nor be damaged by anyone other
 * than the encounter target. The encounter manager owns its lifecycle.</p>
 */
public final class HeraldorServant extends WitherSkeleton {

    private static final String ENCOUNTER_ID = "ZapegServantEncounter";
    private static final String TARGET_ID = "ZapegServantTarget";
    private static final String REHEARSAL = "ZapegServantRehearsal";
    private static final String DEADLINE = "ZapegServantDeadline";
    private static final String ARCHETYPE = "ZapegServantArchetype";
    private static final String NEXT_SPECIAL = "ZapegServantNextSpecial";
    private static final String SPECIAL_RESOLVE = "ZapegServantSpecialResolve";
    private static final String COMPLETED_SPECIALS = "ZapegServantCompletedSpecials";
    private static final int RETRY_SPECIAL_TICKS = 20;

    private static final EntityDataAccessor<Integer> SYNCED_ARCHETYPE =
            SynchedEntityData.defineId(HeraldorServant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SYNCED_TELEGRAPHING =
            SynchedEntityData.defineId(HeraldorServant.class, EntityDataSerializers.BOOLEAN);

    @Nullable private UUID encounterId;
    @Nullable private UUID designatedTargetId;
    private boolean rehearsal;
    private long deadlineGameTime;
    private long nextSpecialGameTime = -1L;
    private long specialResolveGameTime = -1L;
    private int completedSpecials;

    public HeraldorServant(EntityType<? extends WitherSkeleton> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createServantAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 64.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SYNCED_ARCHETYPE, ServantArchetype.STALKER.ordinal());
        entityData.define(SYNCED_TELEGRAPHING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new LoadedMeleeAttackGoal());
        // No generic player-look or target-selector goals. MeleeAttackGoal
        // looks only at the single target installed by customServerAiStep.
    }

    public void configure(
            UUID newEncounterId,
            UUID newTargetId,
            boolean newRehearsal,
            long newDeadlineGameTime) {
        configure(
                newEncounterId,
                newTargetId,
                newRehearsal,
                newDeadlineGameTime,
                ServantArchetype.STALKER);
    }

    public void configure(
            UUID newEncounterId,
            UUID newTargetId,
            boolean newRehearsal,
            long newDeadlineGameTime,
            ServantArchetype newArchetype) {
        if (encounterId != null && !encounterId.equals(newEncounterId)) {
            throw new IllegalStateException("Servant encounter identity is immutable");
        }
        if (designatedTargetId != null && !designatedTargetId.equals(newTargetId)) {
            throw new IllegalStateException("Servant target identity is immutable");
        }
        if (hasEncounterIdentity() && archetype() != newArchetype) {
            throw new IllegalStateException("Servant archetype is immutable");
        }
        encounterId = newEncounterId;
        designatedTargetId = newTargetId;
        rehearsal = newRehearsal;
        deadlineGameTime = newDeadlineGameTime;
        entityData.set(SYNCED_ARCHETYPE, newArchetype.ordinal());
        if (nextSpecialGameTime < 0L) {
            long start = Math.max(0L, newDeadlineGameTime - ServantEncounterManager.LIFETIME_TICKS);
            nextSpecialGameTime = ServantCombatSchedule.addWithoutOverflow(
                    start,
                    ServantCombatSchedule.initialDelay(newEncounterId, newArchetype));
        }
        applyArchetypePresentationAndAttributes(true);
        setPersistenceRequired();
    }

    public boolean hasEncounterIdentity() {
        return encounterId != null && designatedTargetId != null;
    }

    @Nullable
    public UUID encounterId() {
        return encounterId;
    }

    @Nullable
    public UUID designatedTargetId() {
        return designatedTargetId;
    }

    public boolean rehearsal() {
        return rehearsal;
    }

    public long deadlineGameTime() {
        return deadlineGameTime;
    }

    public ServantArchetype archetype() {
        int ordinal = entityData.get(SYNCED_ARCHETYPE);
        ServantArchetype[] values = ServantArchetype.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : ServantArchetype.STALKER;
    }

    public CombatSnapshot combatSnapshot() {
        return new CombatSnapshot(
                archetype(),
                entityData.get(SYNCED_TELEGRAPHING),
                nextSpecialGameTime,
                specialResolveGameTime,
                completedSpecials);
    }

    public boolean identityMatches(ServantEncounter encounter) {
        return getUUID().equals(encounter.servantId())
                && encounter.encounterId().equals(encounterId)
                && encounter.targetId().equals(designatedTargetId)
                && encounter.rehearsal() == rehearsal
                && encounter.deadlineGameTime() == deadlineGameTime
                && encounter.archetype() == archetype();
    }

    @Override
    public void setTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || (safetyAllowsCombat() && isDesignatedTarget(candidate))) {
            super.setTarget(candidate);
        }
    }

    @Override
    public boolean canAttack(LivingEntity candidate) {
        return safetyAllowsCombat()
                && isDesignatedTarget(candidate)
                && super.canAttack(candidate);
    }

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            super.setTarget(null);
            super.customServerAiStep();
            return;
        }
        if (!safetyAllowsCombat()) {
            // Same-tick backstop for an entity whose manager cleanup has not reached it yet.
            super.setTarget(null);
            getNavigation().stop();
            setDeltaMovement(0.0D, 0.0D, 0.0D);
            entityData.set(SYNCED_TELEGRAPHING, false);
            setGlowingTag(false);
            return;
        }
        Player resolved = designatedTargetId == null
                ? null
                : serverLevel.getPlayerByUUID(designatedTargetId);
        ServerPlayer designated = resolved instanceof ServerPlayer serverPlayer
                ? serverPlayer
                : null;
        if (designated == null || !designated.isAlive() || designated.level() != level()) {
            super.setTarget(null);
            getNavigation().stop();
        } else if (!ServantLoadedChunks.movementCorridorLoaded(
                serverLevel, getBoundingBox(), designated.getBoundingBox())) {
            super.setTarget(null);
            getNavigation().stop();
        } else if (getTarget() != designated) {
            super.setTarget(designated);
        }
        super.customServerAiStep();
        if (getTarget() != null && !isDesignatedTarget(getTarget())) {
            super.setTarget(null);
        }
        tickSpecial(serverLevel, designated);
    }

    @Override
    public boolean doHurtTarget(Entity victim) {
        return safetyAllowsCombat()
                && isDesignatedTarget(victim)
                && super.doHurtTarget(victim);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker == null
                || !ServantCombatPolicy.allows(designatedTargetId, attacker.getUUID())) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        boolean deadBeforeDie = dead;
        super.die(source);
        Entity killer = source.getEntity();
        UUID killerId = killer == null ? null : killer.getUUID();
        if (ServantDeathCreditGate.shouldCredit(
                deadBeforeDie, dead, designatedTargetId, killerId)) {
            ServantEncounterManager.onCommittedDeath(this, killerId);
        }
        entityData.set(SYNCED_TELEGRAPHING, false);
        setGlowingTag(false);
    }

    private boolean isDesignatedTarget(Entity candidate) {
        return candidate instanceof ServerPlayer
                && ServantCombatPolicy.allows(designatedTargetId, candidate.getUUID());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return ServantCombatPolicy.DESPAWN_IN_PEACEFUL;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return safetyAllowsCombat()
                && ServantCombatPolicy.preventsRest(designatedTargetId, player.getUUID())
                && super.isPreventingPlayerRest(player);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // No Wither Skeleton skull or equipment drops.
    }

    @Override
    protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
        // Encounter entities are story state, never a farmable loot source.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (encounterId != null) {
            tag.putUUID(ENCOUNTER_ID, encounterId);
        }
        if (designatedTargetId != null) {
            tag.putUUID(TARGET_ID, designatedTargetId);
        }
        tag.putBoolean(REHEARSAL, rehearsal);
        tag.putLong(DEADLINE, deadlineGameTime);
        tag.putString(ARCHETYPE, archetype().id());
        tag.putLong(NEXT_SPECIAL, nextSpecialGameTime);
        tag.putLong(SPECIAL_RESOLVE, specialResolveGameTime);
        tag.putInt(COMPLETED_SPECIALS, completedSpecials);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        encounterId = tag.hasUUID(ENCOUNTER_ID) ? tag.getUUID(ENCOUNTER_ID) : null;
        designatedTargetId = tag.hasUUID(TARGET_ID) ? tag.getUUID(TARGET_ID) : null;
        rehearsal = tag.getBoolean(REHEARSAL);
        deadlineGameTime = tag.getLong(DEADLINE);
        ServantArchetype loadedArchetype = tag.contains(ARCHETYPE, Tag.TAG_STRING)
                ? ServantArchetype.fromId(tag.getString(ARCHETYPE))
                        .orElse(ServantArchetype.STALKER)
                : ServantArchetype.STALKER;
        entityData.set(SYNCED_ARCHETYPE, loadedArchetype.ordinal());
        nextSpecialGameTime = tag.contains(NEXT_SPECIAL, Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong(NEXT_SPECIAL))
                : -1L;
        specialResolveGameTime = tag.contains(SPECIAL_RESOLVE, Tag.TAG_LONG)
                ? tag.getLong(SPECIAL_RESOLVE)
                : -1L;
        if (specialResolveGameTime < 0L || specialResolveGameTime > deadlineGameTime) {
            specialResolveGameTime = -1L;
        }
        completedSpecials = tag.contains(COMPLETED_SPECIALS, Tag.TAG_INT)
                ? Math.max(0, Math.min(
                        tag.getInt(COMPLETED_SPECIALS),
                        ServantCombatSchedule.MAX_SEQUENCE))
                : 0;
        if (encounterId != null && nextSpecialGameTime < 0L) {
            long start = Math.max(0L, deadlineGameTime - ServantEncounterManager.LIFETIME_TICKS);
            nextSpecialGameTime = ServantCombatSchedule.addWithoutOverflow(
                    start,
                    ServantCombatSchedule.initialDelay(encounterId, loadedArchetype));
        }
        entityData.set(SYNCED_TELEGRAPHING, specialResolveGameTime >= 0L);
        setGlowingTag(specialResolveGameTime >= 0L);
        xpReward = 0;
        applyArchetypePresentationAndAttributes(false);
        setPersistenceRequired();
    }

    private boolean canUseLoadedPursuit() {
        LivingEntity target = getTarget();
        if (!(level() instanceof ServerLevel serverLevel)
                || !safetyAllowsCombat()
                || target == null
                || !isDesignatedTarget(target)
                || !target.isAlive()
                || target.level() != level()) {
            return false;
        }
        // No vanilla navigation/path query may occur before both its complete
        // search region and every retained detour node pass read-only residency
        // checks. This method is called immediately before every super goal
        // entry/tick below.
        return ServantLoadedChunks.movementCorridorLoaded(
                        serverLevel, getBoundingBox(), target.getBoundingBox())
                && ServantLoadedChunks.pathfindingFootprintLoaded(
                        serverLevel,
                        blockPosition(),
                        getAttributeValue(Attributes.FOLLOW_RANGE))
                && ServantLoadedChunks.pathNodesLoaded(
                        serverLevel, getNavigation().getPath());
    }

    private boolean safetyAllowsCombat() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        HeraldorSafetyMode required = rehearsal
                ? HeraldorSafetyMode.MANUAL
                : HeraldorSafetyMode.LIVE;
        return HeraldorSafetyController.allows(serverLevel.getServer(), required);
    }

    private void tickSpecial(ServerLevel level, @Nullable ServerPlayer target) {
        if (encounterId == null || designatedTargetId == null || deadlineGameTime <= 0L) {
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        if (now >= deadlineGameTime) {
            cancelTelegraph();
            return;
        }
        if (specialResolveGameTime >= 0L) {
            if ((now & 3L) == 0L && target != null) {
                emitTelegraph(level, target);
            }
            if (now >= specialResolveGameTime) {
                ServantArchetype resolvingArchetype = archetype();
                specialResolveGameTime = -1L;
                entityData.set(SYNCED_TELEGRAPHING, false);
                setGlowingTag(false);
                completedSpecials = Math.min(
                        ServantCombatSchedule.MAX_SEQUENCE,
                        completedSpecials + 1);
                nextSpecialGameTime = ServantCombatSchedule.addWithoutOverflow(
                        now,
                        ServantCombatSchedule.cooldown(
                                encounterId, resolvingArchetype, completedSpecials));
                resolveSpecial(level, target, resolvingArchetype);
            }
            return;
        }
        if (now < nextSpecialGameTime || target == null) {
            return;
        }
        ServantSpecialPolicy.Result eligibility = evaluateSpecial(level, target, archetype());
        if (eligibility != ServantSpecialPolicy.Result.HIT) {
            nextSpecialGameTime = ServantCombatSchedule.addWithoutOverflow(
                    now, RETRY_SPECIAL_TICKS);
            return;
        }
        specialResolveGameTime = ServantCombatSchedule.addWithoutOverflow(
                now, archetype().telegraphTicks());
        entityData.set(SYNCED_TELEGRAPHING, true);
        setGlowingTag(true);
        target.displayClientMessage(Component.translatable(
                archetype().telegraphTranslationKey()), true);
        level.playSound(
                null,
                blockPosition(),
                SoundEvents.WITHER_SKELETON_AMBIENT,
                SoundSource.HOSTILE,
                1.35F,
                telegraphPitch(archetype()));
        emitTelegraph(level, target);
    }

    private ServantSpecialPolicy.Result evaluateSpecial(
            ServerLevel level,
            ServerPlayer target,
            ServantArchetype resolvingArchetype) {
        return ServantSpecialPolicy.evaluate(
                resolvingArchetype,
                new ServantSpecialPolicy.TargetFacts(
                        ServantCombatPolicy.allows(designatedTargetId, target.getUUID()),
                        target.isAlive(),
                        target.level() == level,
                        ServantLoadedChunks.movementCorridorLoaded(
                                level, getBoundingBox(), target.getBoundingBox()),
                        getSensing().hasLineOfSight(target),
                        distanceToSqr(target)));
    }

    private void resolveSpecial(
            ServerLevel level,
            @Nullable ServerPlayer target,
            ServantArchetype resolvingArchetype) {
        if (target == null
                || evaluateSpecial(level, target, resolvingArchetype)
                        != ServantSpecialPolicy.Result.HIT) {
            level.playSound(
                    null,
                    blockPosition(),
                    SoundEvents.WITHER_SKELETON_HURT,
                    SoundSource.HOSTILE,
                    0.8F,
                    0.7F);
            return;
        }
        boolean damaged = target.hurt(
                level.damageSources().mobAttack(this),
                resolvingArchetype.specialDamage());
        if (!damaged) {
            return;
        }
        switch (resolvingArchetype) {
            case STALKER -> target.knockback(
                    0.55D, target.getX() - getX(), target.getZ() - getZ());
            case HERALD -> target.addEffect(
                    new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true, true),
                    this);
            case BINDER -> target.addEffect(
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true),
                    this);
        }
        level.sendParticles(
                resolvingArchetype == ServantArchetype.BINDER
                        ? ParticleTypes.REVERSE_PORTAL
                        : ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(),
                target.getY() + 1.0D,
                target.getZ(),
                18,
                0.55D,
                0.8D,
                0.55D,
                0.02D);
        level.playSound(
                null,
                target.blockPosition(),
                SoundEvents.WITHER_SKELETON_HURT,
                SoundSource.HOSTILE,
                1.2F,
                0.9F + 0.1F * resolvingArchetype.ordinal());
    }

    private void emitTelegraph(ServerLevel level, ServerPlayer target) {
        ServantArchetype active = archetype();
        level.sendParticles(
                active == ServantArchetype.BINDER
                        ? ParticleTypes.REVERSE_PORTAL
                        : ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(),
                target.getY() + 0.15D,
                target.getZ(),
                8,
                active.specialRange() * 0.08D,
                0.08D,
                active.specialRange() * 0.08D,
                0.0D);
        level.sendParticles(
                ParticleTypes.ASH,
                getX(),
                getY() + 1.25D,
                getZ(),
                5,
                0.35D,
                0.7D,
                0.35D,
                0.0D);
    }

    private void cancelTelegraph() {
        specialResolveGameTime = -1L;
        entityData.set(SYNCED_TELEGRAPHING, false);
        setGlowingTag(false);
    }

    private void applyArchetypePresentationAndAttributes(boolean healToFull) {
        ServantArchetype active = archetype();
        setAttributeBase(Attributes.MAX_HEALTH, active.maxHealth());
        setAttributeBase(Attributes.ARMOR, active.armor());
        setAttributeBase(Attributes.ATTACK_DAMAGE, active.attackDamage());
        setAttributeBase(Attributes.MOVEMENT_SPEED, active.movementSpeed());
        setAttributeBase(Attributes.KNOCKBACK_RESISTANCE, active.knockbackResistance());
        if (healToFull) {
            setHealth(getMaxHealth());
        } else if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
        setCustomName(Component.translatable(active.translationKey()));
        setCustomNameVisible(true);
    }

    private void setAttributeBase(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static float telegraphPitch(ServantArchetype archetype) {
        return switch (archetype) {
            case STALKER -> 1.25F;
            case HERALD -> 0.65F;
            case BINDER -> 0.85F;
        };
    }

    public record CombatSnapshot(
            ServantArchetype archetype,
            boolean telegraphing,
            long nextSpecialGameTime,
            long specialResolveGameTime,
            int completedSpecials) {}

    private final class LoadedMeleeAttackGoal extends MeleeAttackGoal {

        private LoadedMeleeAttackGoal() {
            super(HeraldorServant.this, 1.08D, false);
        }

        @Override
        public boolean canUse() {
            return canUseLoadedPursuit() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return canUseLoadedPursuit() && super.canContinueToUse();
        }

        @Override
        public void tick() {
            if (!canUseLoadedPursuit()) {
                HeraldorServant.this.getNavigation().stop();
                return;
            }
            super.tick();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            // Mob.serverAiStep ticks goals before navigation. Forcing this
            // wrapper every tick keeps the footprint check ahead of every
            // PathNavigation.tick/recompute pass.
            return true;
        }
    }
}
