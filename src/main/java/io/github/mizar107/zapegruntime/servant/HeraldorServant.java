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
import io.github.mizar107.zapegruntime.sound.HeraldorSounds;
import net.minecraft.sounds.SoundEvent;
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
