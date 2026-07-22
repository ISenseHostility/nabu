package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.blockentity.GardenControllerBlockEntity;
import ai.jarno.nabu.blockentity.PlantingBedBlockEntity;
import ai.jarno.nabu.blockentity.WaterScrewBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class NabuBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Nabu.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    // 26.2 removed BlockEntityType.Builder -- the constructor is public now.
    public static final RegistrySupplier<BlockEntityType<WaterScrewBlockEntity>> WATER_SCREW =
            BLOCK_ENTITY_TYPES.register(
                    "water_screw",
                    () -> new BlockEntityType<>(
                            WaterScrewBlockEntity::new,
                            Set.of(NabuBlocks.WATER_SCREW.get())));

    /** Non-ticking; pure storage for the bed's link to its controller. */
    public static final RegistrySupplier<BlockEntityType<PlantingBedBlockEntity>> PLANTING_BED =
            BLOCK_ENTITY_TYPES.register(
                    "planting_bed",
                    () -> new BlockEntityType<>(
                            PlantingBedBlockEntity::new,
                            Set.of(NabuBlocks.PLANTING_BED.get())));

    public static final RegistrySupplier<BlockEntityType<GardenControllerBlockEntity>> GARDEN_CONTROLLER =
            BLOCK_ENTITY_TYPES.register(
                    "garden_controller",
                    () -> new BlockEntityType<>(
                            GardenControllerBlockEntity::new,
                            Set.of(NabuBlocks.GARDEN_CONTROLLER.get())));

    private NabuBlockEntities() {
    }

    public static void register() {
        BLOCK_ENTITY_TYPES.register();
    }
}
