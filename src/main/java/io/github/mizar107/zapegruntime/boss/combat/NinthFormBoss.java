package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSnapshot;
import io.github.mizar107.zapegruntime.boss.api.NinthFormCombatSignal;
import io.github.mizar107.zapegruntime.boss.api.NinthFormEntityGateway;
import io.github.mizar107.zapegruntime.boss.api.NinthFormIdentity;
import io.github.mizar107.zapegruntime.boss.api.NinthFormPhase;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * Server-authored Ninth Form parent. The entity is a loaded-world projection;
 * the encounter SavedData remains the sole persistence authority.
 */
public final class NinthFormBoss extends LivingEntity {

    public static final double BASE_HEALTH = 900.0D;
    public static final double FOOTPRINT_RADIUS = 12.0D;
    public static final double CULLING_RADIUS = 18.0D;

    private static final String AUTHORITY = "ZapegNinthFormAuthority";
    private static final String MIRROR = "ZapegNinthFormMirror";
    private static final String SCHEMA = "SchemaVersion";
    private static final String ENCOUNTER = "EncounterId";
    private static final String TARGET = "TargetId";
    private static final String GENERATION = "Generation";
    private static final String REHEARSAL = "Rehearsal";
    private static final String PHASE = "Phase";
    private static final String PARTICIPANTS = "ParticipantCount";
    private static final String HEALTH_SCALE = "HealthScale";
    private static final String DAMAGE_SCALE = "DamageScale";
    private static final String BROKEN_MASK = "BrokenPointMask";
    private static final String ATTACK_CYCLE = "AttackCycle";
    private static final String ATTACK_ID = "AttackId";
    private static final String ATTACK_TICK = "AttackTick";
    private static final String PROW_HEALTH = "ProwHealthFraction";
    private static final String PORT_HEALTH = "PortHealthFraction";
    private static final String STARBOARD_HEALTH = "StarboardHealthFraction";
    private static final int NBT_SCHEMA = 1;
    private static final Set<String> AUTHORITY_FIELDS = Set.of(
            SCHEMA, ENCOUNTER, TARGET, GENERATION, REHEARSAL);
    private static final Set<String> MIRROR_FIELDS = Set.of(
            SCHEMA,
            PHASE,
            PARTICIPANTS,
            HEALTH_SCALE,
            DAMAGE_SCALE,
            BROKEN_MASK,
            ATTACK_CYCLE,
            ATTACK_ID,
            ATTACK_TICK,
            PROW_HEALTH,
            PORT_HEALTH,
            STARBOARD_HEALTH);

    private static final EntityDataAccessor<Integer> SYNCED_PHASE =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SYNCED_BROKEN_MASK =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SYNCED_ATTACK_CYCLE =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> SYNCED_ATTACK_ID =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SYNCED_ATTACK_TICK =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNCED_PROW_HEALTH =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_PORT_HEALTH =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SYNCED_STARBOARD_HEALTH =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SYNCED_PARTICIPANTS =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNCED_DAMAGE_SCALE =
            SynchedEntityData.defineId(NinthFormBoss.class, EntityDataSerializers.FLOAT);

    private final NinthFormPart[] parts;
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("The Ninth Form"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    @Nullable private NinthFormIdentity encounterIdentity;
    private boolean authorityRejected;
    private double healthScale = 1.0D;
    private Consumer<NinthFormCombatSignal> signalSink = ignored -> {};

    public NinthFormBoss(EntityType<? extends NinthFormBoss> type, Level level) {
        super(type, level);
        parts = Arrays.stream(NinthFormPartKind.values())
                .map(kind -> new NinthFormPart(this, kind))
                .toArray(NinthFormPart[]::new);
        // Reserve one contiguous network-id range exactly as the vanilla
        // Ender Dragon does; Forge registers these cached PartEntity objects.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        setNoGravity(true);
        noPhysics = true;
        bossBar.setDarkenScreen(true);
        bossBar.setCreateWorldFog(true);
        bossBar.setPlayBossMusic(false);
        updatePartPositions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SYNCED_PHASE, NinthFormPhase.PRELUDE.ordinal());
        entityData.define(SYNCED_BROKEN_MASK, 0);
        entityData.define(SYNCED_ATTACK_CYCLE, 0L);
        entityData.define(SYNCED_ATTACK_ID, "idle");
        entityData.define(SYNCED_ATTACK_TICK, 0);
        entityData.define(SYNCED_PROW_HEALTH, 1.0F);
        entityData.define(SYNCED_PORT_HEALTH, 1.0F);
        entityData.define(SYNCED_STARBOARD_HEALTH, 1.0F);
        entityData.define(SYNCED_PARTICIPANTS, 1);
        entityData.define(SYNCED_DAMAGE_SCALE, 1.0F);
    }

    /** Installs one immutable encounter identity and its authoritative recovery snapshot. */
    public void configure(NinthFormEntityGateway.SpawnRequest request) {
        Objects.requireNonNull(request, "request");
        request.identity().validateEntityId(getUUID());
        if (encounterIdentity != null && !encounterIdentity.equals(request.identity())) {
            throw new IllegalStateException("Ninth Form encounter identity is immutable");
        }
        if (request.vitalState().parentHealthFraction() <= 0.0D) {
            throw new IllegalArgumentException("a live Ninth Form requires positive parent health");
        }
        encounterIdentity = request.identity();
        authorityRejected = false;
        healthScale = request.healthScale();
        entityData.set(SYNCED_PHASE, request.phase().ordinal());
        entityData.set(SYNCED_PARTICIPANTS, request.participantCount());
        entityData.set(SYNCED_DAMAGE_SCALE, (float) request.damageScale());
        applyCombatState(request.combatState(), request.vitalState());

        AttributeInstance maximum = getAttribute(Attributes.MAX_HEALTH);
        if (maximum == null) {
            throw new IllegalStateException("Ninth Form max-health attribute is unavailable");
        }
        maximum.setBaseValue(BASE_HEALTH * request.healthScale());
        setHealth((float) (getMaxHealth() * request.vitalState().parentHealthFraction()));
        updateBossBar();
    }

    public Optional<NinthFormIdentity> encounterIdentity() {
        return Optional.ofNullable(encounterIdentity);
    }

    void attachSignalSink(Consumer<NinthFormCombatSignal> sink) {
        signalSink = Objects.requireNonNull(sink, "sink");
    }

    void emitCombatSignal(NinthFormCombatSignal.Kind kind, NinthFormPhase signalPhase) {
        NinthFormIdentity identity = encounterIdentity;
        if (identity == null || level().isClientSide) {
            return;
        }
        signalSink.accept(new NinthFormCombatSignal(
                kind,
                identity,
                getUUID(),
                signalPhase,
                identity.targetId(),
                Math.max(0L, level().getGameTime())));
    }

    public boolean identityMatches(NinthFormIdentity expected, UUID entityId) {
        return getUUID().equals(entityId) && expected.equals(encounterIdentity);
    }

    public NinthFormPhase combatPhase() {
        int ordinal = entityData.get(SYNCED_PHASE);
        NinthFormPhase[] values = NinthFormPhase.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : NinthFormPhase.PRELUDE;
    }

    public int brokenPointMask() {
        return entityData.get(SYNCED_BROKEN_MASK);
    }

    public String attackId() {
        return entityData.get(SYNCED_ATTACK_ID);
    }

    public int attackTick() {
        return entityData.get(SYNCED_ATTACK_TICK);
    }

    public long attackCycle() {
        return entityData.get(SYNCED_ATTACK_CYCLE);
    }

    public int participantCount() {
        return entityData.get(SYNCED_PARTICIPANTS);
    }

    public double damageScale() {
        return entityData.get(SYNCED_DAMAGE_SCALE);
    }

    public double weakPointHealth(NinthFormPartKind kind) {
        return switch (kind) {
            case PROW_LANTERN -> entityData.get(SYNCED_PROW_HEALTH);
            case PORT_MOORING -> entityData.get(SYNCED_PORT_HEALTH);
            case STARBOARD_MOORING -> entityData.get(SYNCED_STARBOARD_HEALTH);
            default -> 0.0D;
        };
    }

    public NinthFormCombatSnapshot snapshot(long observedGameTick) {
        NinthFormIdentity identity = Objects.requireNonNull(
                encounterIdentity, "unauthorized Ninth Form has no combat snapshot");
        double maximum = getMaxHealth();
        return new NinthFormCombatSnapshot(
                identity,
                getUUID(),
                combatPhase(),
                level().dimension().location().toString(),
                getX(),
                getY(),
                getZ(),
                Math.max(0.0D, getHealth()),
                maximum,
                participantCount(),
                combatState(),
                new NinthFormCombatSnapshot.VitalState(
                        Math.max(0.0D, getHealth()) / maximum,
                        weakPointHealth(NinthFormPartKind.PROW_LANTERN),
                        weakPointHealth(NinthFormPartKind.PORT_MOORING),
                        weakPointHealth(NinthFormPartKind.STARBOARD_MOORING)),
                observedGameTick);
    }

    public NinthFormCombatSnapshot.CombatState combatState() {
        return new NinthFormCombatSnapshot.CombatState(
                brokenPointMask(), attackCycle(), attackId(), attackTick());
    }

    public boolean transition(NinthFormPhase expected, NinthFormPhase next) {
        if (combatPhase() != expected || !expected.canAdvanceTo(next)) {
            return false;
        }
        entityData.set(SYNCED_PHASE, next.ordinal());
        if (next.terminal()) {
            setHealth(0.0F);
        }
        updateBossBar();
        return true;
    }

    /** Shell routing seam; deterministic weak-point policy replaces this in the combat commit. */
    boolean hurtPart(NinthFormPartKind kind, DamageSource source, float amount) {
        return encounterIdentity != null
                && !authorityRejected
                && amount > 0.0F
                && Float.isFinite(amount)
                && super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        updatePartPositions();
        if (!level().isClientSide) {
            if (authorityRejected || encounterIdentity == null) {
                discard();
                return;
            }
            updateBossBar();
        }
    }

    private void updateBossBar() {
        bossBar.setProgress(getMaxHealth() <= 0.0F
                ? 0.0F
                : Math.max(0.0F, Math.min(1.0F, getHealth() / getMaxHealth())));
    }

    private void updatePartPositions() {
        double radians = Math.toRadians(getYRot());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (NinthFormPart part : parts) {
            NinthFormPartKind kind = part.kind();
            double x = getX() + kind.lateralOffset() * cos - kind.forwardOffset() * sin;
            double z = getZ() + kind.lateralOffset() * sin + kind.forwardOffset() * cos;
            part.setPos(x, getY() + kind.verticalOffset(), z);
            part.setYRot(getYRot());
        }
    }

    public static AABB footprintAt(double x, double y, double z) {
        return new AABB(
                x - FOOTPRINT_RADIUS,
                y - 2.0D,
                z - FOOTPRINT_RADIUS,
                x + FOOTPRINT_RADIUS,
                y + 11.0D,
                z + FOOTPRINT_RADIUS);
    }

    public AABB loadedFootprint() {
        return footprintAt(getX(), getY(), getZ());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB bounds = getBoundingBox();
        for (NinthFormPart part : parts) {
            bounds = bounds.minmax(part.getBoundingBox());
        }
        return bounds.minmax(new AABB(
                getX() - CULLING_RADIUS,
                getY() - 4.0D,
                getZ() - CULLING_RADIUS,
                getX() + CULLING_RADIUS,
                getY() + 14.0D,
                getZ() + CULLING_RADIUS));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        return distanceSquared < 384.0D * 384.0D;
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) {
            for (int index = 0; index < parts.length; index++) {
                parts[index].setId(id + index + 1);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag root) {
        super.addAdditionalSaveData(root);
        if (encounterIdentity == null || authorityRejected) {
            return;
        }
        CompoundTag authority = new CompoundTag();
        authority.putInt(SCHEMA, NBT_SCHEMA);
        authority.putUUID(ENCOUNTER, encounterIdentity.encounterId());
        authority.putUUID(TARGET, encounterIdentity.targetId());
        authority.putInt(GENERATION, encounterIdentity.generation());
        authority.putBoolean(REHEARSAL, encounterIdentity.rehearsal());
        root.put(AUTHORITY, authority);

        CompoundTag mirror = new CompoundTag();
        mirror.putInt(SCHEMA, NBT_SCHEMA);
        mirror.putString(PHASE, combatPhase().name());
        mirror.putInt(PARTICIPANTS, participantCount());
        mirror.putDouble(HEALTH_SCALE, healthScale);
        mirror.putDouble(DAMAGE_SCALE, damageScale());
        mirror.putInt(BROKEN_MASK, brokenPointMask());
        mirror.putLong(ATTACK_CYCLE, attackCycle());
        mirror.putString(ATTACK_ID, attackId());
        mirror.putInt(ATTACK_TICK, attackTick());
        mirror.putDouble(PROW_HEALTH, weakPointHealth(NinthFormPartKind.PROW_LANTERN));
        mirror.putDouble(PORT_HEALTH, weakPointHealth(NinthFormPartKind.PORT_MOORING));
        mirror.putDouble(
                STARBOARD_HEALTH, weakPointHealth(NinthFormPartKind.STARBOARD_MOORING));
        root.put(MIRROR, mirror);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag root) {
        super.readAdditionalSaveData(root);
        try {
            CompoundTag authority = requireCompound(root, AUTHORITY);
            requireFields(authority, AUTHORITY_FIELDS, AUTHORITY);
            requireType(authority, SCHEMA, Tag.TAG_INT, AUTHORITY);
            requireSchema(authority.getInt(SCHEMA), AUTHORITY);
            requireUuid(authority, ENCOUNTER, AUTHORITY);
            requireUuid(authority, TARGET, AUTHORITY);
            requireType(authority, GENERATION, Tag.TAG_INT, AUTHORITY);
            requireType(authority, REHEARSAL, Tag.TAG_BYTE, AUTHORITY);
            NinthFormIdentity loadedIdentity = new NinthFormIdentity(
                    authority.getUUID(ENCOUNTER),
                    authority.getUUID(TARGET),
                    authority.getInt(GENERATION),
                    authority.getBoolean(REHEARSAL));
            loadedIdentity.validateEntityId(getUUID());

            CompoundTag mirror = requireCompound(root, MIRROR);
            readMirror(mirror);
            encounterIdentity = loadedIdentity;
            authorityRejected = false;
            updatePartPositions();
            updateBossBar();
        } catch (IllegalArgumentException invalid) {
            encounterIdentity = null;
            authorityRejected = true;
        }
    }

    private void readMirror(CompoundTag mirror) {
        requireFields(mirror, MIRROR_FIELDS, MIRROR);
        requireType(mirror, SCHEMA, Tag.TAG_INT, MIRROR);
        requireSchema(mirror.getInt(SCHEMA), MIRROR);
        requireType(mirror, PHASE, Tag.TAG_STRING, MIRROR);
        requireType(mirror, PARTICIPANTS, Tag.TAG_INT, MIRROR);
        requireType(mirror, HEALTH_SCALE, Tag.TAG_DOUBLE, MIRROR);
        requireType(mirror, DAMAGE_SCALE, Tag.TAG_DOUBLE, MIRROR);
        requireType(mirror, BROKEN_MASK, Tag.TAG_INT, MIRROR);
        requireType(mirror, ATTACK_CYCLE, Tag.TAG_LONG, MIRROR);
        requireType(mirror, ATTACK_ID, Tag.TAG_STRING, MIRROR);
        requireType(mirror, ATTACK_TICK, Tag.TAG_INT, MIRROR);
        requireType(mirror, PROW_HEALTH, Tag.TAG_DOUBLE, MIRROR);
        requireType(mirror, PORT_HEALTH, Tag.TAG_DOUBLE, MIRROR);
        requireType(mirror, STARBOARD_HEALTH, Tag.TAG_DOUBLE, MIRROR);

        NinthFormPhase loadedPhase;
        try {
            loadedPhase = NinthFormPhase.valueOf(mirror.getString(PHASE));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid Ninth Form phase", invalid);
        }
        int loadedParticipants = mirror.getInt(PARTICIPANTS);
        double loadedHealthScale = requireScale(mirror.getDouble(HEALTH_SCALE), HEALTH_SCALE);
        double loadedDamageScale = requireScale(mirror.getDouble(DAMAGE_SCALE), DAMAGE_SCALE);
        double expectedMaximum = BASE_HEALTH * loadedHealthScale;
        if (!Float.isFinite(getHealth())
                || getHealth() < 0.0F
                || getHealth() > getMaxHealth()
                || Math.abs(getMaxHealth() - expectedMaximum) > 0.001D) {
            throw new IllegalArgumentException("Ninth Form parent vitality is inconsistent");
        }
        NinthFormCombatSnapshot.CombatState combat = new NinthFormCombatSnapshot.CombatState(
                mirror.getInt(BROKEN_MASK),
                mirror.getLong(ATTACK_CYCLE),
                mirror.getString(ATTACK_ID),
                mirror.getInt(ATTACK_TICK));
        NinthFormCombatSnapshot.VitalState vitality = new NinthFormCombatSnapshot.VitalState(
                healthFraction(),
                mirror.getDouble(PROW_HEALTH),
                mirror.getDouble(PORT_HEALTH),
                mirror.getDouble(STARBOARD_HEALTH));
        vitality.validateMask(combat.brokenPointMask());
        if (loadedParticipants < 1 || loadedParticipants > 8) {
            throw new IllegalArgumentException("invalid Ninth Form participant count");
        }
        if (loadedPhase.terminal() != (getHealth() == 0.0F)) {
            throw new IllegalArgumentException("Ninth Form phase conflicts with parent vitality");
        }
        healthScale = loadedHealthScale;
        entityData.set(SYNCED_PHASE, loadedPhase.ordinal());
        entityData.set(SYNCED_PARTICIPANTS, loadedParticipants);
        entityData.set(SYNCED_DAMAGE_SCALE, (float) loadedDamageScale);
        applyCombatState(combat, vitality);
    }

    private void applyCombatState(
            NinthFormCombatSnapshot.CombatState combat,
            NinthFormCombatSnapshot.VitalState vitality) {
        vitality.validateMask(combat.brokenPointMask());
        entityData.set(SYNCED_BROKEN_MASK, combat.brokenPointMask());
        entityData.set(SYNCED_ATTACK_CYCLE, combat.attackCycle());
        entityData.set(SYNCED_ATTACK_ID, combat.attackId());
        entityData.set(SYNCED_ATTACK_TICK, combat.attackTick());
        entityData.set(SYNCED_PROW_HEALTH, (float) vitality.prowHealthFraction());
        entityData.set(SYNCED_PORT_HEALTH, (float) vitality.portHealthFraction());
        entityData.set(SYNCED_STARBOARD_HEALTH, (float) vitality.starboardHealthFraction());
    }

    private double healthFraction() {
        return getMaxHealth() <= 0.0F
                ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, getHealth() / getMaxHealth()));
    }

    private static CompoundTag requireCompound(CompoundTag root, String key) {
        requireType(root, key, Tag.TAG_COMPOUND, "root");
        return root.getCompound(key);
    }

    private static void requireFields(CompoundTag tag, Set<String> fields, String context) {
        if (!tag.getAllKeys().equals(fields)) {
            throw new IllegalArgumentException(context + " fields are not exact");
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String context) {
        Tag value = tag.get(key);
        if (value == null || value.getId() != type) {
            throw new IllegalArgumentException(context + ' ' + key + " has the wrong NBT type");
        }
    }

    private static void requireUuid(CompoundTag tag, String key, String context) {
        requireType(tag, key, Tag.TAG_INT_ARRAY, context);
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException(context + ' ' + key + " is not a UUID");
        }
    }

    private static void requireSchema(int schema, String context) {
        if (schema != NBT_SCHEMA) {
            throw new IllegalArgumentException(context + " has unsupported schema " + schema);
        }
    }

    private static double requireScale(double scale, String name) {
        if (!Double.isFinite(scale) || scale < 0.25D || scale > 8.0D) {
            throw new IllegalArgumentException(name + " is outside [0.25, 8]");
        }
        return scale;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_DEATH;
    }
}
