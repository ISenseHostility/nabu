package ai.jarno.nabu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The soil checks the extinct crops share.
 *
 * <p>Both take the position of the <em>crop</em> and look down one block themselves, so a
 * caller never has to remember which of the two positions a gate wants.
 */
public final class CropGates {
    private CropGates() {
    }

    /** Whether the soil under this crop is a planting bed currently running at BOOSTED. */
    public static boolean isBoosted(LevelReader level, BlockPos cropPos) {
        return PlantingBedBlock.tierAt(level, cropPos.below()) == BedTier.BOOSTED;
    }

    /**
     * Whether the soil under this crop holds any moisture at all.
     *
     * <p>Reads MOISTURE rather than {@link BedTier} on purpose: vanilla farmland carries the
     * same property, so ordinary moist farmland counts exactly like a Watered bed and the
     * crops stay plantable outside the Wonder. Only a bone-dry bed reads false.
     */
    public static boolean isMoist(LevelReader level, BlockPos cropPos) {
        BlockState soil = level.getBlockState(cropPos.below());
        return soil.hasProperty(BlockStateProperties.MOISTURE)
                && soil.getValue(BlockStateProperties.MOISTURE) > 0;
    }
}
