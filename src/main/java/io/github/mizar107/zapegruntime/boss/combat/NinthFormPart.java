package io.github.mizar107.zapegruntime.boss.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

/** One cached Forge-native child hitbox owned and positioned by the parent. */
public final class NinthFormPart extends PartEntity<NinthFormBoss> {

    private final NinthFormPartKind kind;
    private final EntityDimensions dimensions;

    NinthFormPart(NinthFormBoss parent, NinthFormPartKind kind) {
        super(parent);
        this.kind = kind;
        this.dimensions = EntityDimensions.scalable(kind.width(), kind.height());
        refreshDimensions();
    }

    public NinthFormPartKind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return dimensions;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurtPart(kind, source, amount);
    }

    @Override
    public boolean is(Entity candidate) {
        return this == candidate || getParent() == candidate;
    }

    @Override
    public ItemStack getPickResult() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
