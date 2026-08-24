package io.github.mizar107.zapegruntime.journal;

import io.github.mizar107.zapegruntime.ZapeGRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge item registrations for the server-bound hidden journal. */
public final class JournalItems {

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ZapeGRuntime.MOD_ID);

    public static final RegistryObject<Item> HERALDOR_JOURNAL = ITEMS.register(
            "heraldor_journal",
            () -> new HeraldorJournalItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private JournalItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
