package ai.jarno.nabu.block;

import ai.jarno.nabu.blockentity.GardenControllerBlockEntity;
import ai.jarno.nabu.blockentity.PlantingBedBlockEntity;
import ai.jarno.nabu.registry.NabuBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The shrine controller: the thing that knows whether the Gardens are alive again.
 */
public class GardenControllerBlock extends BaseEntityBlock {
    public static final MapCodec<GardenControllerBlock> CODEC = simpleCodec(GardenControllerBlock::new);

    /** Live state, not the permanent unlock: true while at least one bed is currently boosted. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /** Reach of the manual survey described in {@link #useWithoutItem}. */
    private static final int SURVEY_RADIUS = 8;

    public GardenControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public MapCodec<GardenControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GardenControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, NabuBlockEntities.GARDEN_CONTROLLER.get(),
                GardenControllerBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + random.nextDouble(),
                pos.getY() + 1.0 + random.nextDouble() * 0.5,
                pos.getZ() + random.nextDouble(),
                0.0, 0.02, 0.0);
    }

    /**
     * Bootstrap: adopt every planting bed in reach as a Wonder bed.
     *
     * <p>This exists so the completion loop is testable before the structure exists. Once M5
     * generates the Gardens, bed pieces register themselves through their data markers and
     * this becomes a debugging convenience rather than the real path.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GardenControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        int adopted = survey(level, pos, controller);
        player.sendSystemMessage(Component.literal(
                "Surveyed %d bed(s) over %d terrace(s); %d restored, %d boosted now%s".formatted(
                        adopted,
                        controller.knownTerraceCount(),
                        controller.restoredTerraceCount(),
                        controller.liveBoostedBeds(),
                        controller.isCompleted() ? " -- complete" : "")));
        return InteractionResult.SUCCESS;
    }

    private static int survey(Level level, BlockPos pos, GardenControllerBlockEntity controller) {
        int adopted = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-SURVEY_RADIUS, -SURVEY_RADIUS, -SURVEY_RADIUS),
                pos.offset(SURVEY_RADIUS, SURVEY_RADIUS, SURVEY_RADIUS))) {
            if (!(level.getBlockState(candidate).getBlock() instanceof PlantingBedBlock)) {
                continue;
            }
            if (!(level.getBlockEntity(candidate) instanceof PlantingBedBlockEntity bed)) {
                continue;
            }

            // betweenClosed hands back one reused mutable position.
            BlockPos bedPos = candidate.immutable();
            // One terrace per level. M5 replaces this with explicit indices from the markers.
            int terrace = bedPos.getY();

            bed.linkTo(pos, terrace);
            controller.registerBed(terrace, bedPos);
            adopted++;

            // A bed that is already boosted should latch now rather than wait for a transition
            // that has, from its point of view, already happened.
            if (PlantingBedBlock.tierAt(level, bedPos) == BedTier.BOOSTED) {
                bed.reportBoosted(level);
            }
        }
        return adopted;
    }
}
