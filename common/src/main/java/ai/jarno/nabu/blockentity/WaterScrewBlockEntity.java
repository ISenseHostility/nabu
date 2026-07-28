package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.registry.NabuBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
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

    /**
     * Whether real water still reaches this screw, walked up the stack a stage per tick.
     *
     * <p>Deliberately not the same question as "is the screw below running". A draining column
     * holds its lower charges on purpose, so those screws keep reading as running long after
     * the water is gone; this is the signal that survives that and lets the top know to empty.
     */
    private boolean supplied;

    /**
     * Last charge value handed to clients, and the tick it was true on. Clients rebuild the
     * charge by walking forward from here rather than being told it every tick.
     */
    private int chargeSampleValue;

    private long chargeSampleTime;

    /** Ticks per tick the charge is currently moving: positive priming, negative decaying. */
    private int chargeDirection;

    /**
     * Not persisted. Forces the first server tick after load to publish a fresh sample, so a
     * sample restored from disk against a stale timestamp never survives to be extrapolated.
     */
    private boolean chargeSampleDirty = true;

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

    /**
     * The charge at this exact instant, carried to sub-tick precision for rendering.
     *
     * <p>Rebuilt from the last published sample rather than read off {@link #charge}, because
     * clients are only sent a sample when the direction flips. Between flips the charge moves
     * at a fixed rate, so walking forward from the sample lands on precisely the value the
     * server holds -- this is exact, not an approximation, and it costs no per-tick traffic.
     */
    public float charge(float partialTicks) {
        if (level == null) {
            return charge;
        }
        float elapsed = (float) (level.getGameTime() - chargeSampleTime) + partialTicks;
        return Mth.clamp(chargeSampleValue + chargeDirection * elapsed, 0.0F, (float) CHARGE_MAX);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterScrewBlockEntity screw) {
        screw.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        // A real source at the intake: a reservoir, or another screw's delivered water. Stacked
        // screws sit flush with no gap for a source block between them, so those relay instead
        // -- see the two conditions below.
        boolean fed = level.getFluidState(intakePos()).isSourceOfType(Fluids.WATER);

        // Whether real water reaches this screw, relayed up the chain a stage per tick. Kept
        // separate from the charge because it has to stay meaningful while the screws below are
        // still holding theirs -- it is what tells a column its supply is gone, independently
        // of anything still spinning or still standing in a gap.
        supplied = resolveSupplied(level, fed);

        // Priming still waits on the stage below genuinely running, so a column comes up one
        // screw at a time. Requiring the supply on top of that is what keeps a draining chain
        // from deadlocking: water alone at the intake is not enough, because a screw holding
        // its charge on the way down goes on propping up the source it delivered.
        boolean canCharge = supplied && (fed || isRunningScrew(level, intakePos()));

        int direction;
        if (canCharge) {
            charge = Math.min(charge + CHARGE_PER_TICK, CHARGE_MAX);
            direction = CHARGE_PER_TICK;
        } else if (!supplied && chargeAbove(level) <= 0) {
            charge = Math.max(charge - DECAY_PER_TICK, 0);
            direction = -DECAY_PER_TICK;
        } else {
            // Supply is gone but a stage above still holds charge, so sit on ours. A column
            // drains from its surface downward rather than collapsing out from under itself.
            direction = 0;
        }
        // Republish only when the ramp changes direction -- when the supply is gained or lost,
        // or the column starts holding -- rather than every tick. Clamping at either end needs
        // no sample of its own: the client clamps to the same bounds walking forward.
        if (chargeSampleDirty || direction != chargeDirection) {
            chargeDirection = direction;
            chargeSampleValue = charge;
            chargeSampleTime = level.getGameTime();
            chargeSampleDirty = false;
            setChanged();
            // UPDATE_CLIENTS only: this is a visual sample, it must not trigger neighbour updates.
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }

        boolean running = state.getValue(WaterScrewBlock.RUNNING);
        if (!running && charge >= CHARGE_MAX) {
            activate(level, pos, state);
        } else if (running && charge <= 0) {
            deactivate(level, pos, state);
        } else if (running && charge >= CHARGE_MAX) {
            if (maintainOutput(level)) {
                // The delivered source had gone missing and we just put it back. Beds that dried
                // out while it was buried need to hear about it now, not on their next random tick.
                refreshBedsInRange(level);
            }
        } else if (running && placedSource) {
            // Off full charge the lift no longer reaches the top, so the delivery comes straight
            // back rather than standing there for the whole decay. This is output maintenance,
            // not the start/stop decision -- RUNNING keeps its full-to-empty hysteresis, so
            // nothing here can thrash the block state. Only ever our own source, and only while
            // it is still water: clearPlacedSource enforces both.
            clearPlacedSource(level);
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

    /**
     * Charge of the next screw up the chain: stacked flush on us, or across the gap we deliver
     * into. Zero when this is the last stage.
     */
    private int chargeAbove(Level level) {
        BlockPos above = outputPos();
        if (level.getBlockEntity(above) instanceof WaterScrewBlockEntity flush) {
            return flush.charge;
        }
        // Spaced stack: only ever chain through a source we placed ourselves. Water a player
        // dropped into the gap supplies that screw independently of us, so it is not ours to
        // wait on before draining.
        if (placedSource && level.getBlockEntity(above.above()) instanceof WaterScrewBlockEntity spaced) {
            return spaced.charge;
        }
        return 0;
    }

    /**
     * Whether real water still reaches this screw, resolved through whichever shape of chain it
     * sits in.
     *
     * <p>Water standing at the intake is not proof on its own. A screw holding its charge on the
     * way down keeps maintaining the source it delivered, so that block outlives the supply that
     * justified it -- trusting it is exactly what deadlocks a draining stack. When the intake
     * water is one of ours, the answer is inherited from the screw propping it up instead.
     */
    private boolean resolveSupplied(Level level, boolean fed) {
        // Flush stack: the stage below sits directly against us, leaving no room for a source.
        if (level.getBlockEntity(intakePos()) instanceof WaterScrewBlockEntity flush) {
            return flush.supplied;
        }
        // Spaced stack: our intake is the source the screw below the gap delivers into it.
        if (fed
                && level.getBlockEntity(intakePos().below()) instanceof WaterScrewBlockEntity feeder
                && feeder.placedSource
                && feeder.outputPos().equals(intakePos())) {
            return feeder.supplied;
        }
        // Standing water nobody is holding up: the reservoir, rain, a player's bucket.
        return fed;
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

    /** Carries the charge sample to clients; the renderer needs it to fill and turn the screw. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("charge", charge);
        output.putBoolean("placed_source", placedSource);
        // Persisted rather than rederived: on load every screw would otherwise read unsupplied
        // for a tick and a primed column would blip downward before recovering.
        output.putBoolean("supplied", supplied);
        output.putInt("charge_sample_value", chargeSampleValue);
        output.putLong("charge_sample_time", chargeSampleTime);
        output.putInt("charge_direction", chargeDirection);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        charge = input.getIntOr("charge", 0);
        placedSource = input.getBooleanOr("placed_source", false);
        supplied = input.getBooleanOr("supplied", false);
        chargeSampleValue = input.getIntOr("charge_sample_value", charge);
        chargeSampleTime = input.getLongOr("charge_sample_time", 0L);
        chargeDirection = input.getIntOr("charge_direction", 0);
    }
}
