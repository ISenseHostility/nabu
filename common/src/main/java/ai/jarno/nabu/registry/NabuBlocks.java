package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
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

    private NabuBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
