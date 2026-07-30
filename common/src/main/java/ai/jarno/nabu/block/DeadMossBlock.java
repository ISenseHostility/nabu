package ai.jarno.nabu.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The crust left where moss dried out on the brick, and what the shrine turns it back into.
 *
 * <p>A full cube, like the vanilla moss block it becomes. That matters beyond looks: a withered
 * shrub standing on one has to still have a sturdy face beneath it after the sweep passes, and
 * a moss block gives it one -- the trap {@link DeadLeavesBlock} fell into does not apply here.
 *
 * <p>Deliberately not queued for bone meal the way the bare-earth drifts are.
 * {@code MossBlock.performBonemeal} converts the stone around it to moss and raises vegetation
 * on top, which on a monument built entirely of brick would eat the Wonder outward from every
 * patch. Reviving to plain moss is the whole intent.
 *
 * <p>Properties are built up rather than copied from {@link Blocks#MOSS_BLOCK} for the reason
 * {@link DeadLeavesBlock} spells out: {@code ofLegacyCopy} drags state-dependent property
 * functions across, and this block has no state to evaluate them against.
 */
public class DeadMossBlock extends Block implements DeadFoliage {
    public DeadMossBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState revived(BlockState state) {
        return Blocks.MOSS_BLOCK.defaultBlockState();
    }
}
