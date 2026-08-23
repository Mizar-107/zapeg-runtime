package io.github.mizar107.zapegruntime.sound;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Mod-owned, common-side sound registrations used by private horror scenes. */
public final class HeraldorSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZapeGRuntime.MOD_ID);

    public static final RegistryObject<SoundEvent> WHISPER_01 = register("heraldor_whisper_01");
    public static final RegistryObject<SoundEvent> WHISPER_02 = register("heraldor_whisper_02");
    public static final RegistryObject<SoundEvent> KNOCK_01 = register("heraldor_knock_01");
    public static final RegistryObject<SoundEvent> KNOCK_02 = register("heraldor_knock_02");
    public static final RegistryObject<SoundEvent> FOOTSTEP_01 = register("heraldor_footstep_01");
    public static final RegistryObject<SoundEvent> FOOTSTEP_02 = register("heraldor_footstep_02");
    public static final RegistryObject<SoundEvent> MANIFESTATION =
            register("heraldor_manifestation");

    private HeraldorSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ZapeGRuntime.MOD_ID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
