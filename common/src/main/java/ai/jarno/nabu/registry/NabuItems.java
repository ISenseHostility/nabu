package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class NabuItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> WATER_SCREW = ITEMS.register(
            "water_screw",
            () -> new BlockItem(
                    NabuBlocks.WATER_SCREW.get(),
                    new Item.Properties().setId(Nabu.key(Registries.ITEM, "water_screw"))));

    private NabuItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
