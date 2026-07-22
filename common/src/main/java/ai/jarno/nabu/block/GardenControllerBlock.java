package ai.jarno.nabu.block;

import ai.jarno.nabu.blockentity.GardenControllerBlockEntity;
import ai.jarno.nabu.blockentity.PlantingBedBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The shrine controller: the thing that knows whether the Gardens are alive again.
 */
public class GardenControllerBlock extends BaseEntityBlock {
    public static final MapCodec<GardenControllerBlock> CODEC = simpleCodec(GardenControllerBlock::new);

    /** Reach of the manual survey described in {@link #useWithoutItem}. */
    private static final int SURVEY_RADIUS = 8;

    public GardenControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<GardenControllerBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GardenControllerBlockEntity(pos, state);
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
                "Surveyed %d bed(s) over %d terrace(s); %d restored%s".formatted(
                        adopted,
                        controller.knownTerraceCount(),
                        controller.restoredTerraceCount(),
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
            controller.registerTerrace(terrace);
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
