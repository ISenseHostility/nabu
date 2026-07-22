package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.registry.NabuBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Archimedes screw. Lifts water one block: it reads its intake below and, once primed,
 * delivers a water source above.
 *
 * <p>Priming uses a charge counter rather than reacting to the intake directly. Water at the
 * intake ramps the charge up; losing it decays the charge back down. Activation happens at
 * full charge and deactivation only at zero, so a flickering intake can never thrash block
 * updates.
 *
 * <p>The delivered source is <em>tracked</em>: we only ever remove a source we placed
 * ourselves, and only while it is still water. Water the player put there is never touched.
 */
public class WaterScrewBlockEntity extends BlockEntity {
    /** Ticks of uninterrupted intake water needed to start, and to coast down to a stop. */
    public static final int CHARGE_MAX = 40;

    private static final int CHARGE_PER_TICK = 1;
    private static final int DECAY_PER_TICK = 1;

    private int charge;

    /**
     * Whether the source at {@link #outputPos()} is ours to remove. False when the output
     * already held water on activation -- we run, but we did not place it, so we must not
     * delete it.
     */
    private boolean placedSource;

    public WaterScrewBlockEntity(BlockPos pos, BlockState state) {
        super(NabuBlockEntities.WATER_SCREW.get(), pos, state);
    }

    /** Where the screw draws from. A reservoir, or (from M6) another screw's output. */
    public BlockPos intakePos() {
        return worldPosition.below();
    }

    /** Where the screw delivers to. */
    public BlockPos outputPos() {
        return worldPosition.above();
    }

    public int charge() {
        return charge;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterScrewBlockEntity screw) {
        screw.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        boolean intakeSatisfied = level.getFluidState(intakePos()).isSourceOfType(Fluids.WATER);

        charge = intakeSatisfied
                ? Math.min(charge + CHARGE_PER_TICK, CHARGE_MAX)
                : Math.max(charge - DECAY_PER_TICK, 0);

        boolean running = state.getValue(WaterScrewBlock.RUNNING);
        if (!running && charge >= CHARGE_MAX) {
            activate(level, pos, state);
        } else if (running && charge <= 0) {
            deactivate(level, pos, state);
        }
    }

    private void activate(Level level, BlockPos pos, BlockState state) {
        BlockPos output = outputPos();

        // Only claim empty space. If something is already there -- player water, another
        // screw's delivery, or a solid block -- we still run, but we own nothing.
        if (level.getBlockState(output).isAir()) {
            level.setBlock(output, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            placedSource = true;
        } else {
            placedSource = false;
        }

        level.setBlock(pos, state.setValue(WaterScrewBlock.RUNNING, true), Block.UPDATE_ALL);
        setChanged();
    }

    private void deactivate(Level level, BlockPos pos, BlockState state) {
        clearPlacedSource(level);
        level.setBlock(pos, state.setValue(WaterScrewBlock.RUNNING, false), Block.UPDATE_ALL);
        setChanged();
    }

    private void clearPlacedSource(Level level) {
        if (!placedSource) {
            return;
        }
        BlockPos output = outputPos();
        // Someone may have replaced our water in the meantime; only clear it if it is still water.
        if (level.getFluidState(output).isSourceOfType(Fluids.WATER)) {
            level.setBlock(output, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        placedSource = false;
    }

    /**
     * Called by vanilla while this block entity is still alive and the block is genuinely
     * being removed -- notably <em>not</em> on chunk unload, which would otherwise delete a
     * legitimately-delivered source every time the area streamed out.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            clearPlacedSource(level);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("charge", charge);
        output.putBoolean("placed_source", placedSource);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        charge = input.getIntOr("charge", 0);
        placedSource = input.getBooleanOr("placed_source", false);
    }
}
