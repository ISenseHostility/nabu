package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Emmer -- the ancient wheat of Mesopotamia, and the one crop that plants itself.
 *
 * <p>The fruiting gate is inherited untouched: it only reaches its final age on a
 * {@link BedTier#BOOSTED} bed. Both spreading paths hang off that, so neither needs a boost
 * check of its own -- reaching max age already proves the bed was boosted.
 *
 * <p>Emmer spreads two ways. It drifts on its own as random ticks land, and a player can push
 * it deliberately with bone meal. The passive path stays cheap because it runs on every mature
 * plant; the bone meal path is allowed to be thorough because a player triggered it.
 */
public class EmmerBlock extends ExtinctCropBlock {
    public static final MapCodec<EmmerBlock> CODEC = simpleCodec(EmmerBlock::new);

    /** One in this many random ticks on a mature plant attempts a spread. */
    private static final int SPREAD_ODDS = 10;

    private static final int SPREAD_RADIUS = 2;
    private static final int SPREAD_HEIGHT = 1;

    public EmmerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends EmmerBlock> codec() {
        return CODEC;
    }

    /** The grain is the seed, as a carrot is its own seed. There is no separate seed item. */
    @Override
    protected ItemLike getBaseSeedId() {
        return NabuItems.EMMER.get();
    }

    /**
     * Bone meal grows the plant as usual, and once it can grow no further it sows a seedling
     * on a nearby bed instead.
     *
     * <p>The second branch tests {@link #isMaxAge} rather than "growth is capped". Those look
     * interchangeable and are not: a plant on an unwatered bed is also capped, one stage
     * short, and spreading from there would hand the player the reward without ever running a
     * screw. Max age is boosted-only by construction, so gating on it keeps the ladder intact.
     */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        if (super.isValidBonemealTarget(level, pos, state)) {
            return true;
        }
        return isMaxAge(state) && !spreadTargets(level, pos).isEmpty();
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        if (super.isValidBonemealTarget(level, pos, state)) {
            super.performBonemeal(level, random, pos, state);
            return;
        }

        List<BlockPos> targets = spreadTargets(level, pos);
        if (targets.isEmpty()) {
            return;
        }
        sow(level, targets.get(random.nextInt(targets.size())));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Checked before super, which cannot change a max-age plant anyway -- it returns
        // early once the age is at its cap.
        if (isMaxAge(state) && random.nextInt(SPREAD_ODDS) == 0) {
            trySpread(level, pos, random);
        }
        super.randomTick(state, level, pos, random);
    }

    /**
     * Sow one seedling nearby, maybe.
     *
     * <p>Tests a single random candidate rather than scanning the box: this runs on every
     * mature plant, so it has to stay O(1). The cost is that spreading is probabilistic and
     * will not reliably fill the nearest gap, which is the right trade for a passive drift.
     * Bone meal is the reliable route, and it can afford the scan.
     */
    private void trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.offset(
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS,
                random.nextInt(SPREAD_HEIGHT * 2 + 1) - SPREAD_HEIGHT,
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS);

        if (target.equals(pos) || !canSowAt(level, target)) {
            return;
        }
        sow(level, target);
    }

    /**
     * Every spot around this plant that could take a seedling.
     *
     * <p>A full scan of the 5x5x3 box, which would be far too expensive on a random tick but
     * is fine here: only a player swinging bone meal gets us here, and being exhaustive is
     * what makes that feel dependable rather than a coin flip.
     */
    private List<BlockPos> spreadTargets(LevelReader level, BlockPos pos) {
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -SPREAD_RADIUS; dx <= SPREAD_RADIUS; dx++) {
            for (int dz = -SPREAD_RADIUS; dz <= SPREAD_RADIUS; dz++) {
                for (int dy = -SPREAD_HEIGHT; dy <= SPREAD_HEIGHT; dy++) {
                    BlockPos target = pos.offset(dx, dy, dz);
                    if (!target.equals(pos) && canSowAt(level, target)) {
                        targets.add(target);
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Planting beds only. Emmer must never creep into a player's own farmland.
     */
    private boolean canSowAt(LevelReader level, BlockPos target) {
        if (!level.hasChunkAt(target) || !level.isEmptyBlock(target)) {
            return false;
        }
        return level.getBlockState(target.below()).getBlock() instanceof PlantingBedBlock;
    }

    private void sow(ServerLevel level, BlockPos target) {
        level.setBlock(target, getStateForAge(0), Block.UPDATE_CLIENTS);
        // Fired at the seedling, not at the plant that was clicked, so the player can see
        // where the spread actually landed.
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, target, 0);
    }
}
