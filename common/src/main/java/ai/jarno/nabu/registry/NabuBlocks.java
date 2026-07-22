package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.ExtinctCropBlock;
import ai.jarno.nabu.block.GardenControllerBlock;
import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.block.WaterScrewBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    public static final RegistrySupplier<GardenControllerBlock> GARDEN_CONTROLLER = BLOCKS.register(
            "garden_controller",
            () -> new GardenControllerBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.CHISELED_STONE_BRICKS)
                    .strength(3.0F)
                    .setId(Nabu.key(Registries.BLOCK, "garden_controller"))));

    private NabuBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
