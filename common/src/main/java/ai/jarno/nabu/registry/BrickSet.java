package ai.jarno.nabu.registry;

import ai.jarno.nabu.block.NabuStairBlock;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;

/**
 * The four blocks of one decorative variant.
 *
 * <p>Carries both naming stems because they differ: the base is plural ("babylonian_bricks")
 * while the derived forms are singular ("babylonian_brick_stairs"). Deriving one from the other
 * would be guesswork, so both are stated.
 */
public record BrickSet(String baseName,
                       String formPrefix,
                       RegistrySupplier<Block> block,
                       RegistrySupplier<NabuStairBlock> stairs,
                       RegistrySupplier<SlabBlock> slab,
                       RegistrySupplier<WallBlock> wall) {
}
