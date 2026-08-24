package io.github.mizar107.zapegruntime.boss.combat;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Deferred entity registration owned by the Ninth Form combat slice. */
public final class NinthFormEntities {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZapeGRuntime.MOD_ID);

    public static final RegistryObject<EntityType<NinthFormBoss>> NINTH_FORM =
            ENTITY_TYPES.register(
                    "ninth_form",
                    () -> EntityType.Builder.<NinthFormBoss>of(
                                    NinthFormBoss::new, MobCategory.MONSTER)
                            .sized(8.0F, 6.0F)
                            .clientTrackingRange(24)
                            .updateInterval(2)
                            .fireImmune()
                            .build(ZapeGRuntime.MOD_ID + ":ninth_form"));

    private NinthFormEntities() {}

    /** Integration calls this once on the mod bus; the shell makes no global call itself. */
    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(NinthFormEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(NINTH_FORM.get(), NinthFormBoss.createAttributes().build());
    }
}
