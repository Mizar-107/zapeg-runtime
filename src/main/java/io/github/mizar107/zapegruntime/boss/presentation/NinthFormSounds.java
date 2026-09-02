package io.github.mizar107.zapegruntime.boss.presentation;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Presentation-owned sound registry seam; the integration batch registers it once. */
public final class NinthFormSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ZapeGRuntime.MOD_ID);

    public static final RegistryObject<SoundEvent> AWAKENING = register("ninth_form_awakening");
    public static final RegistryObject<SoundEvent> TELEGRAPH = register("ninth_form_telegraph");
    public static final RegistryObject<SoundEvent> WEAKPOINT_BREAK =
            register("ninth_form_weakpoint_break");
    public static final RegistryObject<SoundEvent> BANISH = register("ninth_form_banish");
    public static final RegistryObject<SoundEvent> IMPACT = register("ninth_form_impact");
    public static final RegistryObject<SoundEvent> HURT = register("ninth_form_hurt");
    public static final RegistryObject<SoundEvent> DEATH = register("ninth_form_death");
    public static final RegistryObject<SoundEvent> BED = register("ninth_form_bed");

    private NinthFormSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id =
                ResourceLocation.fromNamespaceAndPath(ZapeGRuntime.MOD_ID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
