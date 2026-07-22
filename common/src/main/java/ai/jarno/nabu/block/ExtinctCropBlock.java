package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A crop lost with the Gardens.
 *
 * <p>Growth and fruiting are separate checks. It will sprout and grow on any soil it can be
 * planted on, so it is never simply dead in your hand -- but the final step into the fruiting
 * stage requires a {@link BedTier#BOOSTED} bed beneath it. Left on ordinary farmland it tops
 * out one stage short: fully grown, permanently barren.
 */
public class ExtinctCropBlock extends CropBlock {
    public static final MapCodec<ExtinctCropBlock> CODEC = simpleCodec(ExtinctCropBlock::new);

    /** Growth-rate multiplier while sitting on a boosted bed. */
    private static final float BOOSTED_GROWTH = 2.0F;

    public ExtinctCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ExtinctCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return NabuItems.SILPHIUM_SEEDS.get();
    }

    private static boolean isBoosted(LevelReader level, BlockPos pos) {
        return PlantingBedBlock.tierAt(level, pos.below()) == BedTier.BOOSTED;
    }

    /** Highest age reachable here: the fruiting stage is boosted-only. */
    private int growthCap(LevelReader level, BlockPos pos) {
        return isBoosted(level, pos) ? getMaxAge() : getMaxAge() - 1;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }

        int age = getAge(state);
        if (age >= growthCap(level, pos)) {
            return;
        }

        float speed = getGrowthSpeed(this, level, pos);
        if (isBoosted(level, pos)) {
            speed *= BOOSTED_GROWTH;
        }

        if (random.nextInt((int) (25.0F / speed) + 1) == 0) {
            level.setBlock(pos, getStateForAge(age + 1), Block.UPDATE_CLIENTS);
        }
    }

    // Bone meal must respect the same gate, or the reward is farmable without ever solving
    // the irrigation.
    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        int current = getAge(state);
        int grown = Math.min(growthCap(level, pos), current + getBonemealAgeIncrease(level));
        if (grown > current) {
            level.setBlock(pos, getStateForAge(grown), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return getAge(state) < growthCap(level, pos);
    }
}
