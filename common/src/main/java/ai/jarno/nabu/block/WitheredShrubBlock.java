package ai.jarno.nabu.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shrub that dried out when the terraces did.
 *
 * <p>Built on {@link VegetationBlock} because 26.2 has no {@code DeadBushBlock} -- the abstract
 * vegetation base is what supplies survival, shape updates and pathfinding for a small plant.
 *
 * <p>Like {@link WitheredVineBlock}, reviving is expressed entirely through
 * {@link BonemealableBlock}, so the shrine's aura picks it up with no controller changes.
 */
public class WitheredShrubBlock extends VegetationBlock implements BonemealableBlock {
    public static final MapCodec<WitheredShrubBlock> CODEC = simpleCodec(WitheredShrubBlock::new);

    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

    public WitheredShrubBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<WitheredShrubBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Anything with a solid top will hold it.
     *
     * <p>Deliberately broader than vegetation's dirt-and-farmland default: this is set dressing
     * to be hand-placed on brick, rubble and terrace beds throughout the ruin, and a soil
     * restriction would simply make most of those placements impossible.
     */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.FERN.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Transforms in place rather than seeding a neighbour. */
    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.GROWER;
    }
}
