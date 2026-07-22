package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.block.PlantingBedBlock;
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
 * The claim is re-checked every tick, so burying the output drops our ownership of it and
 * clearing the obstruction has the screw deliver again.
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

    /**
     * Where the screw draws from: a reservoir, another screw's delivered source, or -- when
     * screws are stacked flush -- the running screw immediately below.
     */
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
        // Stacked screws sit flush against each other, leaving no gap for a source block
        // between them, so a running screw directly below satisfies this one's intake on its
        // own. Charge is still earned from empty, so a column primes one stage at a time and
        // the water only surfaces at the top once every screw beneath it has come up.
        boolean intakeSatisfied = level.getFluidState(intakePos()).isSourceOfType(Fluids.WATER)
                || isRunningScrew(level, intakePos());

        charge = intakeSatisfied
                ? Math.min(charge + CHARGE_PER_TICK, CHARGE_MAX)
                : Math.max(charge - DECAY_PER_TICK, 0);

        boolean running = state.getValue(WaterScrewBlock.RUNNING);
        if (!running && charge >= CHARGE_MAX) {
            activate(level, pos, state);
        } else if (running && charge <= 0) {
            deactivate(level, pos, state);
        } else if (running && maintainOutput(level)) {
            // The delivered source had gone missing and we just put it back. Beds that dried
            // out while it was buried need to hear about it now, not on their next random tick.
            refreshBedsInRange(level);
        }
    }

    private void activate(Level level, BlockPos pos, BlockState state) {
        maintainOutput(level);
        level.setBlock(pos, state.setValue(WaterScrewBlock.RUNNING, true), Block.UPDATE_ALL);
        refreshBedsInRange(level);
        setChanged();
    }

    /**
     * Keep the delivered source standing for as long as the screw runs. The output is not ours
     * to assume: a player can bury it, bucket it, or drop their own source into it at any
     * time, so the claim in {@link #placedSource} is re-checked every tick rather than trusted
     * from whenever the screw started.
     *
     * @return true if this call placed a source -- the output went from missing to flowing.
     */
    private boolean maintainOutput(Level level) {
        BlockPos output = outputPos();

        // Only ever claim empty space.
        if (level.getBlockState(output).isAir()) {
            level.setBlock(output, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            placedSource = true;
            setChanged();
            return true;
        }

        // Something else holds the output -- a player's block, their own water, or another
        // screw's delivery. Whatever displaced ours is not ours to remove later.
        if (placedSource && !level.getFluidState(output).isSourceOfType(Fluids.WATER)) {
            placedSource = false;
            setChanged();
        }
        return false;
    }

    private void deactivate(Level level, BlockPos pos, BlockState state) {
        clearPlacedSource(level);
        level.setBlock(pos, state.setValue(WaterScrewBlock.RUNNING, false), Block.UPDATE_ALL);
        refreshBedsInRange(level);
        setChanged();
    }

    /**
     * Push the new running state to nearby beds instead of having every bed poll for screws.
     * Only ever runs on a start/stop transition, so the bounded scan stays rare.
     */
    private void refreshBedsInRange(Level level) {
        int r = PlantingBedBlock.BOOST_RADIUS;
        int h = PlantingBedBlock.BOOST_HEIGHT;
        for (BlockPos candidate : BlockPos.betweenClosed(
                worldPosition.offset(-r, -h, -r), worldPosition.offset(r, h, r))) {
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof PlantingBedBlock) {
                // betweenClosed reuses one mutable position; beds must not capture it.
                PlantingBedBlock.refresh(level, candidate.immutable(), state);
            }
        }
    }

    private static boolean isRunningScrew(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof WaterScrewBlock && state.getValue(WaterScrewBlock.RUNNING);
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
