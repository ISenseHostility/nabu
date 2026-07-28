package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.item.FertilityCharmItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

public final class NabuItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> WATER_SCREW = ITEMS.register(
            "water_screw",
            () -> new BlockItem(
                    NabuBlocks.WATER_SCREW.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "water_screw"))));

    public static final RegistrySupplier<Item> PLANTING_BED = ITEMS.register(
            "planting_bed",
            () -> new BlockItem(
                    NabuBlocks.PLANTING_BED.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "planting_bed"))));

    public static final RegistrySupplier<Item> GARDEN_CONTROLLER = ITEMS.register(
            "garden_controller",
            () -> new BlockItem(
                    NabuBlocks.GARDEN_CONTROLLER.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "garden_controller"))));

    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> SILPHIUM_SEEDS = ITEMS.register(
            "silphium_seeds",
            () -> new BlockItem(
                    NabuBlocks.SILPHIUM.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "silphium_seeds"))));

    /** The harvest itself -- only obtainable from a crop that reached the fruiting stage. */
    public static final RegistrySupplier<Item> SILPHIUM = ITEMS.register(
            "silphium",
            () -> new Item(tabbed().setId(Nabu.key(Registries.ITEM, "silphium"))));

    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> EMMER_SEEDS = ITEMS.register(
            "emmer_seeds",
            () -> new BlockItem(
                    NabuBlocks.EMMER.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "emmer_seeds"))));

    /** The grain itself -- reserved as a crafting ingredient, no use yet. */
    public static final RegistrySupplier<Item> EMMER = ITEMS.register(
            "emmer",
            () -> new Item(tabbed().setId(Nabu.key(Registries.ITEM, "emmer"))));

    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> JUDEAN_DATE_SEEDS = ITEMS.register(
            "judean_date_seeds",
            () -> new BlockItem(
                    NabuBlocks.JUDEAN_DATE.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "judean_date_seeds"))));

    /**
     * The harvest itself -- only obtainable from a palm that reached the fruiting stage.
     * Apple-tier hunger with much better saturation, as a dense dried fruit should be.
     */
    public static final RegistrySupplier<Item> JUDEAN_DATE = ITEMS.register(
            "judean_date",
            () -> new Item(tabbed()
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.5F)
                            .build())
                    .setId(Nabu.key(Registries.ITEM, "judean_date"))));

    /**
     * One-time trophy for restoring the Gardens. Carried in the offhand, it makes breeding
     * animals occasionally bear more than one young.
     */
    public static final RegistrySupplier<Item> FERTILITY_CHARM = ITEMS.register(
            "fertility_charm",
            () -> new FertilityCharmItem(tabbed()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .setId(Nabu.key(Registries.ITEM, "fertility_charm"))));

    private NabuItems() {
    }

    /** Properties already filed under this mod's creative tab. */
    private static Item.Properties tabbed() {
        return new Item.Properties().arch$tab(NabuCreativeTabs.MAIN);
    }

    /** Registers a block item for each of a variant's four blocks, in creative-tab order. */
    private static void blockItems(BrickSet set) {
        blockItem(set.baseName(), set.block());
        blockItem(set.formPrefix() + "_stairs", set.stairs());
        blockItem(set.formPrefix() + "_slab", set.slab());
        blockItem(set.formPrefix() + "_wall", set.wall());
    }

    private static void blockItem(String name, RegistrySupplier<? extends Block> block) {
        ITEMS.register(
                name,
                () -> new BlockItem(
                        block.get(),
                        tabbed().setId(Nabu.key(Registries.ITEM, name))));
    }

    public static void register() {
        // Registered here rather than in field initialisers so the sixteen decorative blocks
        // land after the Wonder's own items in the creative tab.
        blockItems(NabuBlocks.BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.CRACKED_BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.MOSSY_BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.BABYLONIAN_TILES);
        blockItems(NabuBlocks.CHISELED_BABYLONIAN_BRICKS);
        ITEMS.register();
    }
}
