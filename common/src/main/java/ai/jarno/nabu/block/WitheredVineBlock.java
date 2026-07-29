package ai.jarno.nabu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.Map;

/**
 * Vines that died when the water stopped.
 *
 * <p>Reviving is the entire behaviour, and it is expressed purely as {@link BonemealableBlock}.
 * That is not incidental: the shrine's fertility aura already routes every hit through this
 * interface, so implementing it is the whole integration -- the ruin greens up as terraces come
 * online with no change to the controller at all. Bone meal in hand works for the same reason.
 *
 * <p>Because the aura reads <em>live</em> Boosted state, the greening tracks the working garden
 * rather than the one-time unlock, which is the invariant separating those two readings.
 *
 * <p>{@code codec()} is deliberately not overridden. {@link VineBlock} declares it as an
 * invariant {@code MapCodec<VineBlock>}, so no subclass can legally narrow it; this inherits
 * vine's, exactly as {@link NabuStairBlock} inherits the stair codec.
 */
public class WitheredVineBlock extends VineBlock implements BonemealableBlock {
    public WitheredVineBlock(Properties properties) {
        super(properties);
    }

    /**
     * Dead vines do not creep. Vine's own {@code randomTick} spreads across nearby faces, which
     * would have the ruin overgrow itself while it is still supposed to look abandoned.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return false;
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
        level.setBlock(pos, living(state), Block.UPDATE_ALL);
    }

    /** Living vine wearing this block's faces, so a revived vine hangs exactly where the dead one did. */
    private static BlockState living(BlockState state) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> face : PROPERTY_BY_DIRECTION.entrySet()) {
            BooleanProperty property = face.getValue();
            if (state.hasProperty(property) && vine.hasProperty(property)) {
                vine = vine.setValue(property, state.getValue(property));
            }
        }
        return vine;
    }

    /** Transforms in place rather than seeding a neighbour. */
    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.GROWER;
    }
}
