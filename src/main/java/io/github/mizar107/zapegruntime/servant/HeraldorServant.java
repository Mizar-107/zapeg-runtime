package io.github.mizar107.zapegruntime.servant;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
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

    @Nullable private UUID encounterId;
    @Nullable private UUID designatedTargetId;
    private boolean rehearsal;
    private long deadlineGameTime;

    public HeraldorServant(EntityType<? extends WitherSkeleton> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createServantAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 48.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.08D, false));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.75D));
        // No generic player-look or target-selector goals. MeleeAttackGoal
        // looks only at the single target installed by customServerAiStep.
    }

    public void configure(
            UUID newEncounterId,
            UUID newTargetId,
            boolean newRehearsal,
            long newDeadlineGameTime) {
        if (encounterId != null && !encounterId.equals(newEncounterId)) {
            throw new IllegalStateException("Servant encounter identity is immutable");
        }
        if (designatedTargetId != null && !designatedTargetId.equals(newTargetId)) {
            throw new IllegalStateException("Servant target identity is immutable");
        }
        encounterId = newEncounterId;
        designatedTargetId = newTargetId;
        rehearsal = newRehearsal;
        deadlineGameTime = newDeadlineGameTime;
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

    public boolean identityMatches(ServantEncounter encounter) {
        return getUUID().equals(encounter.servantId())
                && encounter.encounterId().equals(encounterId)
                && encounter.targetId().equals(designatedTargetId)
                && encounter.rehearsal() == rehearsal
                && encounter.deadlineGameTime() == deadlineGameTime;
    }

    @Override
    public void setTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || isDesignatedTarget(candidate)) {
            super.setTarget(candidate);
        }
    }

    @Override
    public boolean canAttack(LivingEntity candidate) {
        return isDesignatedTarget(candidate) && super.canAttack(candidate);
    }

    @Override
    protected void customServerAiStep() {
        if (level() instanceof ServerLevel serverLevel && designatedTargetId != null) {
            Player resolved = serverLevel.getPlayerByUUID(designatedTargetId);
            ServerPlayer designated = resolved instanceof ServerPlayer serverPlayer
                    ? serverPlayer
                    : null;
            if (designated == null || !designated.isAlive() || designated.level() != level()) {
                super.setTarget(null);
                getNavigation().stop();
            } else if (getTarget() != designated) {
                super.setTarget(designated);
            }
        } else {
            super.setTarget(null);
        }
        super.customServerAiStep();
        if (getTarget() != null && !isDesignatedTarget(getTarget())) {
            super.setTarget(null);
        }
    }

    @Override
    public boolean doHurtTarget(Entity victim) {
        return isDesignatedTarget(victim) && super.doHurtTarget(victim);
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
        return ServantCombatPolicy.preventsRest(designatedTargetId, player.getUUID())
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        encounterId = tag.hasUUID(ENCOUNTER_ID) ? tag.getUUID(ENCOUNTER_ID) : null;
        designatedTargetId = tag.hasUUID(TARGET_ID) ? tag.getUUID(TARGET_ID) : null;
        rehearsal = tag.getBoolean(REHEARSAL);
        deadlineGameTime = tag.getLong(DEADLINE);
        xpReward = 0;
        setPersistenceRequired();
    }
}
