package io.github.mizar107.zapegruntime.servant;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge registrations owned by the Servant vertical slice. */
public final class ServantEntities {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZapeGRuntime.MOD_ID);

    public static final RegistryObject<EntityType<HeraldorServant>> SERVANT = ENTITY_TYPES.register(
            "servant",
            () -> EntityType.Builder.<HeraldorServant>of(HeraldorServant::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.4F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build(ZapeGRuntime.MOD_ID + ":servant"));

    private ServantEntities() {}

    /** Called once from the mod constructor's mod event bus. */
    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ServantEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(SERVANT.get(), HeraldorServant.createServantAttributes().build());
    }
}
