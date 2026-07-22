package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.registry.NabuBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Per-bed bookkeeping, and the gate on what counts toward the Wonder.
 *
 * <p>A bed only reports upstream if something linked it to a controller. Beds a player places
 * elsewhere keep {@code controller == null} and stay inert forever, so a private screw farm
 * built outside the Gardens can never advance completion.
 */
public class PlantingBedBlockEntity extends BlockEntity {
    public static final int NO_TERRACE = Integer.MIN_VALUE;

    private @Nullable BlockPos controller;
    private int terrace = NO_TERRACE;

    public PlantingBedBlockEntity(BlockPos pos, BlockState state) {
        super(NabuBlockEntities.PLANTING_BED.get(), pos, state);
    }

    public boolean isWonderBed() {
        return controller != null && terrace != NO_TERRACE;
    }

    public void linkTo(BlockPos controllerPos, int terraceIndex) {
        this.controller = controllerPos;
        this.terrace = terraceIndex;
        setChanged();
    }

    /** Report a fresh boost upstream. A no-op for beds that are not part of the Wonder. */
    public void reportBoosted(Level level) {
        if (!isWonderBed() || level.isClientSide() || !level.hasChunkAt(controller)) {
            return;
        }
        if (level.getBlockEntity(controller) instanceof GardenControllerBlockEntity garden) {
            garden.onTerraceRestored(terrace);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("controller", BlockPos.CODEC, controller);
        output.putInt("terrace", terrace);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        controller = input.read("controller", BlockPos.CODEC).orElse(null);
        terrace = input.getIntOr("terrace", NO_TERRACE);
    }
}
