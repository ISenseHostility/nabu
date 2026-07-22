package ai.jarno.nabu.block;

import ai.jarno.nabu.blockentity.WaterScrewBlockEntity;
import ai.jarno.nabu.registry.NabuBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

public class WaterScrewBlock extends BaseEntityBlock {
    public static final MapCodec<WaterScrewBlock> CODEC = simpleCodec(WaterScrewBlock::new);

    /**
     * Replicated to clients for free, so the renderer and the splash particles both read the
     * running state without any custom block-entity sync.
     */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public WaterScrewBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    public MapCodec<WaterScrewBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterScrewBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // Priming and water delivery are server-authoritative; the client only renders.
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, NabuBlockEntities.WATER_SCREW.get(), WaterScrewBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        // Splash where the screw tips its water out, one block up.
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;

        level.addParticle(ParticleTypes.SPLASH, x, y, z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.02, 0.0);
        }
    }

    /**
     * Runs after the screw is already gone, which is exactly what nearby beds need: they can
     * re-read the world and drop out of boosted without still seeing this block running.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        int r = PlantingBedBlock.BOOST_RADIUS;
        int h = PlantingBedBlock.BOOST_HEIGHT;
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-r, -h, -r), pos.offset(r, h, r))) {
            BlockState neighbour = level.getBlockState(candidate);
            if (neighbour.getBlock() instanceof PlantingBedBlock) {
                PlantingBedBlock.refresh(level, candidate.immutable(), neighbour);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
