package ai.jarno.nabu.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plain stair block with a public constructor.
 *
 * <p>{@link StairBlock}'s only constructor is {@code protected}. NeoForge patches it to public;
 * Fabric does not; {@code common} compiles against the vanilla signature either way, so
 * {@code new StairBlock(...)} is not available to us. Subclassing is the portable way in.
 *
 * <p>Adds no behaviour. {@code codec()} is deliberately not overridden -- it correctly inherits
 * {@code StairBlock.CODEC}, whose declared return type {@code MapCodec<? extends StairBlock>}
 * is covariant and so permits this subclass.
 */
public class NabuStairBlock extends StairBlock {
    public NabuStairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(baseState, properties);
    }
}
