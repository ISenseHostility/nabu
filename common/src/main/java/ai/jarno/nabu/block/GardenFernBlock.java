package ai.jarno.nabu.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fern a {@link WitheredShrubBlock} becomes once the shrine greens the ruin.
 *
 * <p>This exists rather than reviving straight into {@code minecraft:fern} because vanilla's
 * fern is a {@code TallGrassBlock} and inherits {@link VegetationBlock}'s soil-only
 * {@code mayPlaceOn}: dirt or farmland, nothing else. The Gardens are brick. A vanilla fern
 * placed on a terrace deck fails {@code canSurvive} on the very neighbour update the sweep's
 * own {@code setBlock} triggers, so the greening would appear to work and then delete itself
 * within a tick. Carrying the withered shrub's broader "anything sturdy will hold it" rule
 * across into the living form is the whole reason this block is here.
 *
 * <p>Rendering borrows vanilla's greyscale fern sprite and tints it with
 * {@code BlockTintSources.grass()}, registered client-side alongside the shrine's water tint.
 * Swap the texture reference in {@code models/block/garden_fern.json} for a hand-drawn one
 * whenever there is art for it -- nothing in the code depends on which sprite it is.
 */
public class GardenFernBlock extends VegetationBlock {
    public static final MapCodec<GardenFernBlock> CODEC = simpleCodec(GardenFernBlock::new);

    /** Vanilla fern's own footprint, and the same one the withered shrub uses. */
    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

    public GardenFernBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<GardenFernBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Anything with a solid top will hold it -- see {@link WitheredShrubBlock#mayPlaceOn}. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }
}
