package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A shrub that dried out when the terraces did.
 *
 * <p>Built on {@link VegetationBlock} because 26.2 has no {@code DeadBushBlock} -- the abstract
 * vegetation base is what supplies survival, shape updates and pathfinding for a small plant.
 *
 * <p>Revives with the rest of the dead growth through the shrine's sweep; see {@link DeadFoliage}.
 */
public class WitheredShrubBlock extends VegetationBlock implements DeadFoliage {
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

    /**
     * Deliberately <em>not</em> {@code minecraft:fern}. Vanilla's fern keeps vegetation's
     * soil-only survival rule, so on a brick terrace it would be culled on the same neighbour
     * update that placed it -- the greening would flash and vanish. {@link GardenFernBlock}
     * carries this block's broader {@link #mayPlaceOn} across into the living form.
     */
    @Override
    public @Nullable BlockState revived(BlockState state) {
        return NabuBlocks.GARDEN_FERN.get().defaultBlockState();
    }
}
