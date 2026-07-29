package ai.jarno.nabu.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The canopy of the Gardens, dead on the branch.
 *
 * <p>A plain {@link Block} rather than a {@code LeavesBlock} subclass, and that is the point:
 * vanilla leaves carry {@code distance}/{@code persistent} and decay when no log is near. These
 * are part of a ruin nobody is maintaining, so decay would quietly delete the set dressing out
 * of a structure that has no trees in it to begin with.
 *
 * <p>Properties are built up explicitly instead of copied from {@link Blocks#OAK_LEAVES}.
 * {@code ofLegacyCopy} carries across state-dependent property functions, and a function written
 * against leaf state evaluated on a block with no such properties throws while the state
 * definition is being built -- the same trap {@link JudeanDateBlock} documents, which takes the
 * game down at registration rather than failing the build.
 */
public class DeadLeavesBlock extends Block implements DeadFoliage {
    public DeadLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState revived(BlockState state) {
        return Blocks.OAK_LEAVES.defaultBlockState();
    }
}
