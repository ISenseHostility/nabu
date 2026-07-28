package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.EmmerBlock;
import ai.jarno.nabu.block.ExtinctCropBlock;
import ai.jarno.nabu.block.GardenControllerBlock;
import ai.jarno.nabu.block.JudeanDateBlock;
import ai.jarno.nabu.block.NabuStairBlock;
import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.block.WaterScrewBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class NabuBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<WaterScrewBlock> WATER_SCREW = BLOCKS.register(
            "water_screw",
            () -> new WaterScrewBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.OAK_PLANKS)
                    .strength(2.0F)
                    .noOcclusion()
                    .setId(Nabu.key(Registries.BLOCK, "water_screw"))));

    public static final RegistrySupplier<PlantingBedBlock> PLANTING_BED = BLOCKS.register(
            "planting_bed",
            () -> new PlantingBedBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.FARMLAND)
                    .randomTicks()
                    .setId(Nabu.key(Registries.BLOCK, "planting_bed"))));

    public static final RegistrySupplier<ExtinctCropBlock> SILPHIUM = BLOCKS.register(
            "silphium",
            () -> new ExtinctCropBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.WHEAT)
                    .setId(Nabu.key(Registries.BLOCK, "silphium"))));

    public static final RegistrySupplier<EmmerBlock> EMMER = BLOCKS.register(
            "emmer",
            () -> new EmmerBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.WHEAT)
                    .setId(Nabu.key(Registries.BLOCK, "emmer"))));

    /**
     * Copies {@link Blocks#PITCHER_CROP}, not {@code WHEAT}, and must keep doing so.
     *
     * <p>Wheat's properties carry a state-dependent map colour -- it reads
     * {@code CropBlock.AGE} to turn yellow when nearly ripe -- and
     * {@code ofLegacyCopy} copies that function along with everything else. This block is a
     * {@code DoublePlantBlock} with {@code AGE_4}, not a {@code CropBlock} with
     * {@code AGE_7}, so evaluating that function while building the state definition throws
     * and takes the whole game down at registration.
     *
     * <p>Pitcher crop is the block this one is modelled on, shares its exact state shape
     * ({@code AGE_4} plus {@code HALF}), and carries no state-dependent properties at all.
     * Silphium and Emmer may safely copy wheat because they really are {@code CropBlock}s.
     */
    public static final RegistrySupplier<JudeanDateBlock> JUDEAN_DATE = BLOCKS.register(
            "judean_date",
            () -> new JudeanDateBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.PITCHER_CROP)
                    .setId(Nabu.key(Registries.BLOCK, "judean_date"))));

    public static final RegistrySupplier<GardenControllerBlock> GARDEN_CONTROLLER = BLOCKS.register(
            "garden_controller",
            () -> new GardenControllerBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.CHISELED_STONE_BRICKS)
                    .strength(3.0F)
                    .noOcclusion()
                    .setId(Nabu.key(Registries.BLOCK, "garden_controller"))));

    public static final BrickSet BABYLONIAN_BRICKS =
            brickSet("babylonian_bricks", "babylonian_brick");

    public static final BrickSet CRACKED_BABYLONIAN_BRICKS =
            brickSet("cracked_babylonian_bricks", "cracked_babylonian_brick");

    public static final BrickSet MOSSY_BABYLONIAN_BRICKS =
            brickSet("mossy_babylonian_bricks", "mossy_babylonian_brick");

    public static final BrickSet POLISHED_BABYLONIAN_BRICKS =
            brickSet("polished_babylonian_bricks", "polished_babylonian_brick");

    public static final BrickSet SMOOTH_BABYLONIAN_BRICKS =
            brickSet("smooth_babylonian_bricks", "smooth_babylonian_brick");

    public static final BrickSet BABYLONIAN_TILES =
            brickSet("babylonian_tiles", "babylonian_tile");

    public static final BrickSet MOSSY_BABYLONIAN_TILES =
            brickSet("mossy_babylonian_tiles", "mossy_babylonian_tile");

    public static final BrickSet CHISELED_BABYLONIAN_BRICKS =
            brickSet("chiseled_babylonian_bricks", "chiseled_babylonian_brick");

    public static final BrickSet GLAZED_BABYLONIAN_BRICKS =
            brickSet("glazed_babylonian_bricks", "glazed_babylonian_brick");

    private NabuBlocks() {
    }

    /**
     * Registers one decorative variant: the base block plus its stairs, slab and wall.
     * The base is registered first so the stairs can read its default state.
     */
    private static BrickSet brickSet(String baseName, String formPrefix) {
        RegistrySupplier<Block> base = BLOCKS.register(
                baseName,
                () -> new Block(stoneLike(baseName)));

        return new BrickSet(
                baseName,
                formPrefix,
                base,
                BLOCKS.register(
                        formPrefix + "_stairs",
                        () -> new NabuStairBlock(
                                base.get().defaultBlockState(),
                                stoneLike(formPrefix + "_stairs"))),
                BLOCKS.register(
                        formPrefix + "_slab",
                        () -> new SlabBlock(stoneLike(formPrefix + "_slab"))),
                BLOCKS.register(
                        formPrefix + "_wall",
                        () -> new WallBlock(stoneLike(formPrefix + "_wall"))));
    }

    /** Stone-brick behaviour plus the per-block id that 26.x requires every block to carry. */
    private static BlockBehaviour.Properties stoneLike(String name) {
        return BlockBehaviour.Properties.ofLegacyCopy(Blocks.STONE_BRICKS)
                .setId(Nabu.key(Registries.BLOCK, name));
    }

    public static void register() {
        BLOCKS.register();
    }
}
