package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Emmer -- the ancient wheat of Mesopotamia, and the one crop that plants itself.
 *
 * <p>The fruiting gate is inherited untouched: it only reaches its final age on a
 * {@link BedTier#BOOSTED} bed. Everything below hangs off that, so neither the regrowing
 * harvest nor the self-seeding needs a boost check of its own -- reaching max age already
 * proves the bed was boosted.
 */
public class EmmerBlock extends ExtinctCropBlock {
    public static final MapCodec<EmmerBlock> CODEC = simpleCodec(EmmerBlock::new);

    /**
     * Age a harvested head falls back to. Three growth steps short of fruiting again, so
     * re-harvesting beats replanting from seed without being free.
     */
    private static final int REGROWN_AGE = 4;

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

    @Override
    protected ItemLike getBaseSeedId() {
        return NabuItems.EMMER_SEEDS.get();
    }

    /**
     * Pick the grain by hand and leave the plant standing.
     *
     * <p>Only offered at full growth, which is boosted-only, so this is a reward for the
     * irrigation rather than a way around it. Breaking the block by hand still yields seeds,
     * which stays the way to get planting stock.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!isMaxAge(state)) {
            // Not ripe: fall through to normal interaction rather than eating the click.
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel server) {
            RandomSource random = server.getRandom();
            popResource(server, pos, new ItemStack(NabuItems.EMMER.get(), 1 + random.nextInt(2)));
            server.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.8F + random.nextFloat() * 0.4F);
            server.setBlock(pos, getStateForAge(REGROWN_AGE), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.SUCCESS;
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
     * will not reliably fill the nearest gap, which is the right trade here.
     *
     * <p>Planting beds only. Emmer must never creep into a player's own farmland.
     */
    private void trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.offset(
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS,
                random.nextInt(SPREAD_HEIGHT * 2 + 1) - SPREAD_HEIGHT,
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS);

        if (target.equals(pos) || !level.hasChunkAt(target)) {
            return;
        }
        if (!level.isEmptyBlock(target)) {
            return;
        }
        if (!(level.getBlockState(target.below()).getBlock() instanceof PlantingBedBlock)) {
            return;
        }

        level.setBlock(target, getStateForAge(0), Block.UPDATE_CLIENTS);
    }
}
