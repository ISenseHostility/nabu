package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.item.ClayTabletItem;
import ai.jarno.nabu.item.FertilityCharmItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
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

    /**
     * The grain, and the thing you sow. Emmer has no separate seed item: the harvest replants
     * itself the way a carrot does, so a field costs you grain to expand rather than a second
     * currency to keep track of.
     */
    public static final RegistrySupplier<Item> EMMER = ITEMS.register(
            "emmer",
            () -> new BlockItem(
                    NabuBlocks.EMMER.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "emmer"))));

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
     * Refined silphium -- {@code laserpicium}, the resin that was the actual traded product.
     * Not food itself; it is the input both of silphium's uses share.
     */
    public static final RegistrySupplier<Item> SILPHIUM_RESIN = ITEMS.register(
            "silphium_resin",
            () -> new Item(tabbed().setId(Nabu.key(Registries.ITEM, "silphium_resin"))));

    /**
     * Date syrup -- {@code dibs}, Mesopotamia's sweetener before honey was common. Bottled,
     * which costs three properties rather than one: it is drunk rather than eaten, drinking
     * hands back the empty bottle, and so does <em>crafting</em> with it, so baking a cake
     * does not quietly swallow the glass. A honey bottle behaves the same three ways.
     */
    public static final RegistrySupplier<Item> DATE_SYRUP = ITEMS.register(
            "date_syrup",
            () -> new Item(tabbed()
                    .food(
                            new FoodProperties.Builder()
                                    .nutrition(2)
                                    .saturationModifier(0.4F)
                                    .build(),
                            Consumables.DEFAULT_DRINK)
                    .usingConvertsTo(Items.GLASS_BOTTLE)
                    .craftRemainder(Items.GLASS_BOTTLE)
                    .stacksTo(16)
                    .setId(Nabu.key(Registries.ITEM, "date_syrup"))));

    /** What emmer was actually for. A shade better than vanilla bread's 5 / 0.6. */
    public static final RegistrySupplier<Item> EMMER_BREAD = ITEMS.register(
            "emmer_bread",
            () -> new Item(tabbed()
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationModifier(0.8F)
                            .build())
                    .setId(Nabu.key(Registries.ITEM, "emmer_bread"))));

    /** Cooked meat rubbed with resin. Saturation-led, because seasoning is the whole point. */
    public static final RegistrySupplier<Item> SPICED_MEAT = ITEMS.register(
            "spiced_meat",
            () -> new Item(tabbed()
                    .food(new FoodProperties.Builder()
                            .nutrition(7)
                            .saturationModifier(1.1F)
                            .build())
                    .setId(Nabu.key(Registries.ITEM, "spiced_meat"))));

    /**
     * Grain and syrup meeting directly -- the one point where two chains cross, and the payoff
     * for having run both.
     */
    public static final RegistrySupplier<Item> DATE_CAKE = ITEMS.register(
            "date_cake",
            () -> new Item(tabbed()
                    .food(new FoodProperties.Builder()
                            .nutrition(8)
                            .saturationModifier(1.0F)
                            .build())
                    .setId(Nabu.key(Registries.ITEM, "date_cake"))));

    public static final RegistrySupplier<Item> WITHERED_VINE = ITEMS.register(
            "withered_vine",
            () -> new BlockItem(
                    NabuBlocks.WITHERED_VINE.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "withered_vine"))));

    public static final RegistrySupplier<Item> WITHERED_SHRUB = ITEMS.register(
            "withered_shrub",
            () -> new BlockItem(
                    NabuBlocks.WITHERED_SHRUB.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "withered_shrub"))));

    public static final RegistrySupplier<Item> DEAD_LEAVES = ITEMS.register(
            "dead_leaves",
            () -> new BlockItem(
                    NabuBlocks.DEAD_LEAVES.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "dead_leaves"))));

    /**
     * The scribe's account of the Gardens, and the mod's only in-world explanation of the
     * puzzle. Seeded into the Wonder's chest as its own guaranteed pool.
     */
    public static final RegistrySupplier<Item> CLAY_TABLET = ITEMS.register(
            "clay_tablet",
            () -> new ClayTabletItem(tabbed()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
                    .setId(Nabu.key(Registries.ITEM, "clay_tablet"))));

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
        blockItems(NabuBlocks.POLISHED_BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.SMOOTH_BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.BABYLONIAN_TILES);
        blockItems(NabuBlocks.MOSSY_BABYLONIAN_TILES);
        blockItems(NabuBlocks.CHISELED_BABYLONIAN_BRICKS);
        blockItems(NabuBlocks.GLAZED_BABYLONIAN_BRICKS);
        ITEMS.register();
    }
}
