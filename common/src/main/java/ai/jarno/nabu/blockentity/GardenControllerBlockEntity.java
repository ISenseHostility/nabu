package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.BedTier;
import ai.jarno.nabu.block.GardenControllerBlock;
import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.registry.NabuBlockEntities;
import ai.jarno.nabu.registry.NabuItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks restoration of the Gardens, terrace by terrace, and radiates life while they run.
 *
 * <p>Two readings live here and are deliberately never derived from one another:
 * {@link #restored} is the permanent unlock, latched forever once earned; {@link #liveBoostedBeds()}
 * is the current state of the world, and fades the moment the screws stop.
 */
public class GardenControllerBlockEntity extends BlockEntity {
    private static final int AURA_INTERVAL_TICKS = 20;
    private static final int AURA_RADIUS = 8;
    private static final int AURA_VERTICAL = 2;
    private static final int AURA_ATTEMPTS = 4;
    private static final float AURA_CHANCE = 0.25F;

    /** Terraces with at least one registered Wonder bed. Discovered, never assumed. */
    private final Set<Integer> known = new LinkedHashSet<>();

    /**
     * Terraces restored at least once. Latched on purpose: a terrace stays counted even if the
     * player later rips the screws out. This is the permanent unlock and must never be
     * recomputed from current tiers.
     */
    private final Set<Integer> restored = new LinkedHashSet<>();

    /** Registered Wonder beds, used only for the live reading behind the aura. */
    private final Set<BlockPos> beds = new LinkedHashSet<>();

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

    public int bedCount() {
        return beds.size();
    }

    public void registerBed(int terrace, BlockPos bedPos) {
        boolean changed = known.add(terrace);
        changed |= beds.add(bedPos.immutable());
        if (changed) {
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
        // The trophy materialises on the altar.
        Block.popResource(level, worldPosition.above(), new ItemStack(NabuItems.FERTILITY_CHARM.get()));
    }

    /**
     * How many registered beds are boosted <em>right now</em>. Read fresh from the world every
     * time; nothing about it is latched, so tearing out the screws fades the aura.
     */
    public int liveBoostedBeds() {
        Level level = getLevel();
        if (level == null) {
            return 0;
        }
        int live = 0;
        for (BlockPos bed : beds) {
            if (level.hasChunkAt(bed) && PlantingBedBlock.tierAt(level, bed) == BedTier.BOOSTED) {
                live++;
            }
        }
        return live;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GardenControllerBlockEntity garden) {
        if (level.getGameTime() % AURA_INTERVAL_TICKS != 0L) {
            return;
        }

        boolean powered = garden.liveBoostedBeds() > 0;
        if (state.getValue(GardenControllerBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(GardenControllerBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (powered && level instanceof ServerLevel server) {
            garden.radiate(server, pos);
        }
    }

    /**
     * Nudge growing things nearby. Routed through {@link BonemealableBlock} on purpose, so the
     * aura obeys the same gate everything else does -- it cannot push an extinct crop into
     * fruiting on an unboosted bed.
     */
    private void radiate(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < AURA_ATTEMPTS; attempt++) {
            if (random.nextFloat() >= AURA_CHANCE) {
                continue;
            }
            BlockPos target = pos.offset(
                    random.nextInt(AURA_RADIUS * 2 + 1) - AURA_RADIUS,
                    random.nextInt(AURA_VERTICAL * 2 + 1) - AURA_VERTICAL,
                    random.nextInt(AURA_RADIUS * 2 + 1) - AURA_RADIUS);

            BlockState targetState = level.getBlockState(target);
            if (targetState.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, target, targetState)) {
                bonemealable.performBonemeal(level, random, target, targetState);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                        3, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("completed", completed);
        output.putIntArray("known", known.stream().mapToInt(Integer::intValue).toArray());
        output.putIntArray("restored", restored.stream().mapToInt(Integer::intValue).toArray());
        output.store("beds", BlockPos.CODEC.listOf(), List.copyOf(beds));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        completed = input.getBooleanOr("completed", false);
        known.clear();
        restored.clear();
        beds.clear();
        input.getIntArray("known").ifPresent(values -> Arrays.stream(values).boxed().forEach(known::add));
        input.getIntArray("restored").ifPresent(values -> Arrays.stream(values).boxed().forEach(restored::add));
        input.read("beds", BlockPos.CODEC.listOf()).ifPresent(beds::addAll);
    }
}
