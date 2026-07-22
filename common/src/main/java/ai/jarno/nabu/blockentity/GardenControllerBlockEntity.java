package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.registry.NabuBlockEntities;
import ai.jarno.nabu.registry.NabuItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks restoration of the Gardens, terrace by terrace.
 *
 * <p>Reacts to beds reporting in; it never polls them. Terrace membership is discovered from
 * whatever beds register themselves, so nothing here assumes how many beds or terraces a
 * generated Wonder happens to have.
 */
public class GardenControllerBlockEntity extends BlockEntity {
    /** Terraces with at least one registered Wonder bed. Discovered, never assumed. */
    private final Set<Integer> known = new LinkedHashSet<>();

    /**
     * Terraces restored at least once. Latched on purpose: a terrace stays counted even if the
     * player later rips the screws out. This is the permanent unlock, and is deliberately not
     * the same reading as the live fertility aura, which tracks current boosted state.
     */
    private final Set<Integer> restored = new LinkedHashSet<>();

    private boolean completed;

    public GardenControllerBlockEntity(BlockPos pos, BlockState state) {
        super(NabuBlockEntities.GARDEN_CONTROLLER.get(), pos, state);
    }

    public boolean isCompleted() {
        return completed;
    }

    public int knownTerraceCount() {
        return known.size();
    }

    public int restoredTerraceCount() {
        return restored.size();
    }

    public void registerTerrace(int terrace) {
        if (known.add(terrace)) {
            setChanged();
        }
    }

    public void onTerraceRestored(int terrace) {
        if (!known.contains(terrace) || !restored.add(terrace)) {
            // Either not a terrace we own, or already latched.
            return;
        }
        setChanged();
        Nabu.LOGGER.info("Terrace {} restored ({}/{}).", terrace, restored.size(), known.size());
        checkCompletion();
    }

    private void checkCompletion() {
        if (completed || known.isEmpty() || !restored.containsAll(known)) {
            return;
        }
        completed = true;
        setChanged();
        onCompleted();
    }

    /** Fires exactly once per controller, guarded by {@link #completed}. */
    private void onCompleted() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        Nabu.LOGGER.info("Hanging Gardens restored at {} across {} terrace(s).", worldPosition, known.size());
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        // Placeholder until M4 wakes the dryad and grants the Fertility Charm.
        Block.popResource(level, worldPosition.above(), new ItemStack(NabuItems.SILPHIUM.get()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("completed", completed);
        output.putIntArray("known", known.stream().mapToInt(Integer::intValue).toArray());
        output.putIntArray("restored", restored.stream().mapToInt(Integer::intValue).toArray());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        completed = input.getBooleanOr("completed", false);
        known.clear();
        restored.clear();
        input.getIntArray("known").ifPresent(values -> Arrays.stream(values).boxed().forEach(known::add));
        input.getIntArray("restored").ifPresent(values -> Arrays.stream(values).boxed().forEach(restored::add));
    }
}
