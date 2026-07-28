package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Judean date palm -- extinct since antiquity, and the only crop here that stands two
 * blocks tall.
 *
 * <p>Alone among the extinct crops it reads the whole bed ladder rather than just the top
 * rung. On a bone-dry bed it never straightens up at all; once merely watered it reaches
 * full height but stays barren; only a {@link BedTier#BOOSTED} bed carries it the last step
 * into fruit. Height and fruit are two separate rewards for two separate rungs.
 *
 * <p>Modelled on vanilla's {@code PitcherCropBlock} but deliberately not extending it: that
 * class declares {@code codec()} as an invariant {@code MapCodec<PitcherCropBlock>}, so no
 * subclass can legally override it, and all of its growth helpers are private.
 */
public class JudeanDateBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final MapCodec<JudeanDateBlock> CODEC = simpleCodec(JudeanDateBlock::new);

    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;

    /** Age from which the palm stands two blocks tall. */
    private static final int DOUBLE_FROM = 2;

    /** One in this many random ticks advances the plant. */
    private static final int GROWTH_ODDS = 6;

    private static final VoxelShape SHAPE_SPROUT = Block.column(12.0, 0.0, 6.0);
    private static final VoxelShape SHAPE_STALK = Block.column(12.0, 0.0, 16.0);

    public JudeanDateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public MapCodec<? extends JudeanDateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, HALF);
    }

    /** Whether the palm is tall enough to own the block above it. */
    private static boolean isDouble(int age) {
        return age >= DOUBLE_FROM;
    }

    // --- placement -------------------------------------------------------------------
    // DoublePlantBlock assumes a plant that is two blocks tall the moment it is placed.
    // This one is not: it starts as a single-block sprout and only grows its top half at
    // DOUBLE_FROM. Both of the following must be overridden, or planting a seed leaves a
    // floating orphan top block.

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        // Intentionally empty: no upper half exists yet. Do not call super.
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_CROPS);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    /**
     * While still a sprout, survive on the soil check alone.
     *
     * <p>{@link DoublePlantBlock}'s version turns any half into air unless the matching other
     * half is adjacent -- correct for a plant that is always two blocks, fatal for one that
     * spends its first two ages as a single block. Below {@code DOUBLE_FROM} there is no upper
     * half to pair with, so the paired check would delete the sprout on the first neighbour
     * update from above.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (isDouble(state.getValue(AGE))) {
            return super.updateShape(state, level, tickAccess, pos, direction, neighborPos,
                    neighborState, random);
        }
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isDouble(state.getValue(AGE)) ? SHAPE_STALK : SHAPE_SPROUT;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(NabuItems.JUDEAN_DATE_SEEDS.get());
    }

    // --- growth ----------------------------------------------------------------------

    /**
     * Highest age reachable here. Dry soil stops it just short of standing up, moist soil
     * just short of fruiting, and only a boosted bed lets it finish.
     */
    private int growthCap(LevelReader level, BlockPos lowerPos) {
        if (CropGates.isBoosted(level, lowerPos)) {
            return MAX_AGE;
        }
        if (CropGates.isMoist(level, lowerPos)) {
            return MAX_AGE - 1;
        }
        return DOUBLE_FROM - 1;
    }

    /** Only the lower half ticks; it drives both. Otherwise the halves race and double-grow. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }

        int age = state.getValue(AGE);
        if (age >= growthCap(level, pos) || !hasRoomToReach(level, pos, age, age + 1)) {
            return;
        }

        if (random.nextInt(GROWTH_ODDS) == 0) {
            grow(level, pos, age + 1);
        }
    }

    /**
     * Whether the step from {@code from} to {@code to} is physically possible.
     *
     * <p>Only the single-to-double transition needs space: past that the upper half is
     * already ours and standing there. A palm under a ceiling stalls rather than eating the
     * block above it.
     */
    private static boolean hasRoomToReach(LevelReader level, BlockPos lowerPos, int from, int to) {
        return isDouble(from) || !isDouble(to) || level.isEmptyBlock(lowerPos.above());
    }

    /** Write the new age to both halves at once, creating the upper one if it is time. */
    private void grow(ServerLevel level, BlockPos lowerPos, int age) {
        level.setBlock(lowerPos,
                defaultBlockState().setValue(AGE, age).setValue(HALF, DoubleBlockHalf.LOWER),
                Block.UPDATE_CLIENTS);
        if (isDouble(age)) {
            level.setBlock(lowerPos.above(),
                    defaultBlockState().setValue(AGE, age).setValue(HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_ALL);
        }
    }

    /** Bone meal may be applied to either half; growth always happens from the lower one. */
    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // --- bone meal -------------------------------------------------------------------
    // Routed through growthCap like everything else. Bone meal must never be a way around
    // the irrigation.

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos lower = lowerPos(state, pos);
        int age = state.getValue(AGE);
        return age < growthCap(level, lower) && hasRoomToReach(level, lower, age, age + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos lower = lowerPos(state, pos);
        int age = state.getValue(AGE);
        int grown = Math.min(growthCap(level, lower), age + 1);
        if (grown > age && hasRoomToReach(level, lower, age, grown)) {
            grow(level, lower, grown);
        }
    }
}
