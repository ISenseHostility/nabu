package ai.jarno.nabu.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Vines that died when the water stopped.
 *
 * <p>Reviving happens only through the shrine's sweep -- see {@link DeadFoliage} for why this is
 * no longer a {@code BonemealableBlock}.
 *
 * <p>{@code codec()} is deliberately not overridden. {@link VineBlock} declares it as an
 * invariant {@code MapCodec<VineBlock>}, so no subclass can legally narrow it; this inherits
 * vine's, exactly as {@link NabuStairBlock} inherits the stair codec.
 */
public class WitheredVineBlock extends VineBlock implements DeadFoliage {
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

    /** Living vine wearing this block's faces, so a revived vine hangs exactly where the dead one did. */
    @Override
    public @Nullable BlockState revived(BlockState state) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> face : PROPERTY_BY_DIRECTION.entrySet()) {
            BooleanProperty property = face.getValue();
            if (state.hasProperty(property) && vine.hasProperty(property)) {
                vine = vine.setValue(property, state.getValue(property));
            }
        }
        return vine;
    }
}
