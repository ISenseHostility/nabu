package ai.jarno.nabu.block;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Growth that died when the water stopped, and what it becomes when the water returns.
 *
 * <p>Implemented rather than hardcoded in the sweep so the shrine never has to know the roster:
 * adding another dead plant is a new block and nothing else.
 *
 * <p>Deliberately <em>not</em> {@code BonemealableBlock}. These used to revive through the
 * fertility aura, which meant bone meal in hand greened the ruin without solving anything. The
 * only path now is the shrine's sweep, so the garden coming back is something the player earns.
 */
public interface DeadFoliage {
    /**
     * The living block this should turn into.
     *
     * @return the revived state, or {@code null} if this particular state has nothing to become
     */
    @Nullable BlockState revived(BlockState state);
}
