package ai.jarno.nabu.block;

import ai.jarno.nabu.advancement.GardenProgressTrigger;
import ai.jarno.nabu.blockentity.PlantingBedBlockEntity;
import ai.jarno.nabu.registry.NabuSounds;
import ai.jarno.nabu.registry.NabuTriggers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A planting bed of the Hanging Gardens.
 *
 * <p>Moisture works the way vanilla farmland's does, so these behave like ordinary farmland for
 * ordinary crops. Unlike farmland they never revert to dirt -- they are part of the Wonder's
 * fixed geometry, and letting them crumble would erode the structure itself.
 */
public class PlantingBedBlock extends Block implements EntityBlock {
    public static final MapCodec<PlantingBedBlock> CODEC = simpleCodec(PlantingBedBlock::new);

    public static final EnumProperty<BedTier> TIER = EnumProperty.create("tier", BedTier.class);
    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;

    public static final int MAX_MOISTURE = 7;

    /** How far a running screw reaches to boost a bed. */
    public static final int BOOST_RADIUS = 4;
    public static final int BOOST_HEIGHT = 1;

    private static final int WATER_RADIUS = 4;

    /** How close a player must be to a blooming bed to be credited for it. */
    private static final double BLOOM_TRIGGER_RADIUS = 16.0;

    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 15.0);

    public PlantingBedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(MOISTURE, 0)
                .setValue(TIER, BedTier.DRY));
    }

    @Override
    public MapCodec<PlantingBedBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOISTURE, TIER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = isWet(level, pos)
                ? MAX_MOISTURE
                : Math.max(state.getValue(MOISTURE) - 1, 0);
        apply(level, pos, state, moisture);
    }

    /**
     * Recompute this bed after something nearby changed -- a screw starting or stopping.
     * Screws call this on their own transitions so boosting is immediate rather than waiting
     * on a random tick.
     *
     * <p>Wetting is picked up here too, since a screw that just delivered has changed whether
     * this bed is near water and the tier now depends on that. Drying is deliberately left to
     * the random tick, so a bed fades out gradually rather than snapping back the instant a
     * screw stops.
     */
    public static void refresh(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof PlantingBedBlock) {
            int moisture = isWet(level, pos) ? MAX_MOISTURE : state.getValue(MOISTURE);
            apply(level, pos, state, moisture);
        }
    }

    private static void apply(Level level, BlockPos pos, BlockState state, int moisture) {
        BedTier previous = state.getValue(TIER);
        BedTier tier = computeTier(level, pos, moisture);
        if (state.getValue(MOISTURE) == moisture && previous == tier) {
            return;
        }

        // Same block, so the block entity survives this and its link is still intact below.
        level.setBlock(pos, state.setValue(MOISTURE, moisture).setValue(TIER, tier), Block.UPDATE_CLIENTS);

        if (tier != BedTier.BOOSTED || previous == BedTier.BOOSTED) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof PlantingBedBlockEntity bed) {
            bed.reportBoosted(level);
        }
        bloom(level, pos);
    }

    /**
     * The moment a bed comes to life. Fires on the tier edge only, so it is one chime per bloom
     * rather than one per refresh.
     *
     * <p>Pitch is detuned per bed on purpose. A single screw activating calls
     * {@code refreshBedsInRange}, which can flip several beds in the very same tick; at a fixed
     * pitch those pile into one smeared flam, and spread out they chord instead.
     */
    private static void bloom(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        float pitch = 0.9F + server.getRandom().nextFloat() * 0.2F;
        server.playSound(null, pos, NabuSounds.PLANTING_BED_BLOOM.get(), SoundSource.BLOCKS, 0.6F, pitch);
        NabuTriggers.fireNearby(server, pos, BLOOM_TRIGGER_RADIUS, GardenProgressTrigger.Stage.BOOSTED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlantingBedBlockEntity(pos, state);
    }

    private static BedTier computeTier(LevelReader level, BlockPos pos, int moisture) {
        // The tiers are a ladder, not three independent states. A bed has to be wet at all
        // before a screw can lift it the rest of the way, so a bone-dry bed sitting next to a
        // running screw reads DRY until the water actually arrives. The anti-cheese still
        // holds from the other side: water alone tops out at WATERED however it got there, so
        // a player-placed source can never reach BOOSTED.
        if (moisture <= 0) {
            return BedTier.DRY;
        }
        return runningScrewInRange(level, pos) ? BedTier.BOOSTED : BedTier.WATERED;
    }

    /** Whether anything is watering this bed right now: water in range, or rain overhead. */
    private static boolean isWet(Level level, BlockPos pos) {
        return nearWater(level, pos) || level.isRainingAt(pos.above());
    }

    private static boolean runningScrewInRange(LevelReader level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-BOOST_RADIUS, -BOOST_HEIGHT, -BOOST_RADIUS),
                pos.offset(BOOST_RADIUS, BOOST_HEIGHT, BOOST_RADIUS))) {
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof WaterScrewBlock && state.getValue(WaterScrewBlock.RUNNING)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearWater(LevelReader level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-WATER_RADIUS, 0, -WATER_RADIUS),
                pos.offset(WATER_RADIUS, 1, WATER_RADIUS))) {
            if (level.getFluidState(candidate).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Tier of the bed at {@code pos}, or {@code null} if that is not a planting bed. */
    public static @Nullable BedTier tierAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PlantingBedBlock ? state.getValue(TIER) : null;
    }
}
