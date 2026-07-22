package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class NabuItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> WATER_SCREW = ITEMS.register(
            "water_screw",
            () -> new BlockItem(
                    NabuBlocks.WATER_SCREW.get(),
                    new Item.Properties().setId(Nabu.key(Registries.ITEM, "water_screw"))));

    public static final RegistrySupplier<Item> PLANTING_BED = ITEMS.register(
            "planting_bed",
            () -> new BlockItem(
                    NabuBlocks.PLANTING_BED.get(),
                    new Item.Properties().setId(Nabu.key(Registries.ITEM, "planting_bed"))));

    public static final RegistrySupplier<Item> GARDEN_CONTROLLER = ITEMS.register(
            "garden_controller",
            () -> new BlockItem(
                    NabuBlocks.GARDEN_CONTROLLER.get(),
                    new Item.Properties().setId(Nabu.key(Registries.ITEM, "garden_controller"))));

    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> SILPHIUM_SEEDS = ITEMS.register(
            "silphium_seeds",
            () -> new BlockItem(
                    NabuBlocks.SILPHIUM.get(),
                    new Item.Properties().setId(Nabu.key(Registries.ITEM, "silphium_seeds"))));

    /** The harvest itself -- only obtainable from a crop that reached the fruiting stage. */
    public static final RegistrySupplier<Item> SILPHIUM = ITEMS.register(
            "silphium",
            () -> new Item(new Item.Properties().setId(Nabu.key(Registries.ITEM, "silphium"))));

    /**
     * One-time trophy for restoring the Gardens. Carried in the offhand, it makes breeding
     * animals occasionally bear more than one young.
     */
    public static final RegistrySupplier<Item> FERTILITY_CHARM = ITEMS.register(
            "fertility_charm",
            () -> new Item(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .setId(Nabu.key(Registries.ITEM, "fertility_charm"))));

    private NabuItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
