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
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The shrine controller: the thing that knows whether the Gardens are alive again.
 */
public class GardenControllerBlock extends BaseEntityBlock {
    public static final MapCodec<GardenControllerBlock> CODEC = simpleCodec(GardenControllerBlock::new);

    /** Live state, not the permanent unlock: true while at least one bed is currently boosted. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private static final int SURVEY_RADIUS_HORIZONTAL = GardenControllerBlockEntity.REACH_HORIZONTAL;
    private static final int SURVEY_RADIUS_VERTICAL = GardenControllerBlockEntity.REACH_VERTICAL;

    /**
     * Traces the font silhouette rather than a full cube, so the selection outline follows the
     * stepped plinth and nobody bumps invisible air at the waist. Kept in step with
     * {@code assets/nabu/models/block/shrine_shell.json} -- the basin interior is deliberately
     * solid here, so the rim is something you can stand on.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),      // base course
            Block.box(1, 2, 1, 15, 4, 15),      // first step
            Block.box(3, 4, 3, 13, 7, 13),      // waist
            Block.box(1, 7, 1, 15, 9, 15),      // corbel
            Block.box(0, 9, 0, 16, 14, 16),     // basin floor and rim
            Block.box(0, 14, 0, 3, 16, 3),      // corner caps
            Block.box(13, 14, 0, 16, 16, 3),
            Block.box(0, 14, 13, 3, 16, 16),
            Block.box(13, 14, 13, 16, 16, 16));

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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
                pos.offset(-SURVEY_RADIUS_HORIZONTAL, -SURVEY_RADIUS_VERTICAL, -SURVEY_RADIUS_HORIZONTAL),
                pos.offset(SURVEY_RADIUS_HORIZONTAL, SURVEY_RADIUS_VERTICAL, SURVEY_RADIUS_HORIZONTAL))) {
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
