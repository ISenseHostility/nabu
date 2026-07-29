package ai.jarno.nabu.blockentity;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.advancement.GardenProgressTrigger;
import ai.jarno.nabu.block.BedTier;
import ai.jarno.nabu.block.DeadFoliage;
import ai.jarno.nabu.block.GardenControllerBlock;
import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.registry.NabuBlockEntities;
import ai.jarno.nabu.registry.NabuItems;
import ai.jarno.nabu.registry.NabuSounds;
import ai.jarno.nabu.registry.NabuTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
    private static final int ADOPT_INTERVAL_TICKS = 100;

    /**
     * Reach for finding beds. Wider than tall, and tall enough to look well above itself: the
     * shrine stands at the reservoir while the beds it tracks sit on the terrace overhead.
     */
    public static final int REACH_HORIZONTAL = 16;
    public static final int REACH_VERTICAL = 12;

    // Reaches upward as well as outward, so a shrine standing at the reservoir still touches
    // the terrace planting above it.
    private static final int AURA_RADIUS = 12;
    private static final int AURA_VERTICAL = 8;
    private static final int AURA_ATTEMPTS = 4;
    private static final float AURA_CHANCE = 0.25F;

    /** How close a player must be to the shrine to be credited for its milestones. */
    private static final double TRIGGER_RADIUS = 16.0;

    /** Tick of the last layer of the awakening swell, and the resting value between swells. */
    private static final int AWAKEN_LAST_TICK = 45;
    private static final int AWAKEN_IDLE = -1;

    /**
     * Reach of the greening sweep. Wider than the aura, because the dead growth dresses the whole
     * ruin rather than clustering on the terraces the shrine waters.
     */
    private static final int GREEN_RADIUS = 24;
    private static final int GREEN_HEIGHT = 16;
    private static final int GREEN_IDLE = Integer.MIN_VALUE;

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

    /**
     * Position in the awakening flourish, or {@link #AWAKEN_IDLE} when it is not playing.
     * Transient by design -- see {@link #tickAwakening}.
     */
    private int awakenTicks = AWAKEN_IDLE;

    /**
     * Whether the dead growth has already been brought back. Persisted, and one-way: the greening
     * is a thing that happened to the world, not a reading of current state, so letting a bed dry
     * out later must never un-grow a tree.
     */
    private boolean greened;

    /**
     * Y offset the sweep is currently working through, or {@link #GREEN_IDLE} when it is not
     * running. Transient on purpose -- {@link #greened} is only set once the last layer is done,
     * so a restart mid-sweep simply runs it again from the bottom. Converting an already-living
     * block is a no-op, which is what makes replaying it safe.
     */
    private int greenSweepY = GREEN_IDLE;

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

        if (getLevel() instanceof ServerLevel server) {
            server.playSound(null, worldPosition, NabuSounds.TERRACE_RESTORED.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            NabuTriggers.fireNearby(server, worldPosition, TRIGGER_RADIUS, GardenProgressTrigger.Stage.TERRACE);
        }

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

        // Opens the swell; the layers themselves are played from the tick below.
        awakenTicks = 0;

        if (level instanceof ServerLevel server) {
            NabuTriggers.fireNearby(server, worldPosition, TRIGGER_RADIUS, GardenProgressTrigger.Stage.AWAKENED);
        }

        // The trophy materialises on the altar.
        Block.popResource(level, worldPosition.above(), new ItemStack(NabuItems.FERTILITY_CHARM.get()));
    }

    /**
     * Plays the awakening as three layers rather than one hit, so completion lands as a moment.
     *
     * <p>Driven off {@link #awakenTicks}, which is deliberately <em>not</em> persisted: a
     * two-second flourish is not state that matters, and saving it would have a server restart
     * replay half a fanfare at a player who has already had the moment. The {@link #completed}
     * latch this hangs off is persisted, and remains what guarantees it fires once.
     */
    private void tickAwakening(Level level, BlockPos pos) {
        switch (awakenTicks) {
            case 0 -> level.playSound(
                    null, pos, NabuSounds.SHRINE_AWAKEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            case 20 -> level.playSound(
                    null, pos, NabuSounds.SHRINE_AWAKEN_CHIME.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            case AWAKEN_LAST_TICK -> level.playSound(
                    null, pos, NabuSounds.SHRINE_AWAKEN_CHIME.get(), SoundSource.BLOCKS, 0.8F, 1.5F);
            default -> {
                // Between layers.
            }
        }
        awakenTicks = awakenTicks >= AWAKEN_LAST_TICK ? AWAKEN_IDLE : awakenTicks + 1;
    }

    /**
     * Bring the ruin's dead growth back, one horizontal layer per tick.
     *
     * <p>Sliced rather than swept in one pass because the volume is large enough that doing it in
     * a single tick would hitch the server -- and the house rule is that block-entity ticks stay
     * cheap. A layer is a few thousand lookups, the whole thing finishes in under two seconds,
     * and it has the side benefit of reading as the green climbing the terraces.
     */
    private void tickGreenSweep(Level level, BlockPos pos) {
        int revived = 0;
        for (int dx = -GREEN_RADIUS; dx <= GREEN_RADIUS; dx++) {
            for (int dz = -GREEN_RADIUS; dz <= GREEN_RADIUS; dz++) {
                BlockPos target = pos.offset(dx, greenSweepY, dz);
                // Never force-load: growth in unloaded chunks simply stays dead.
                if (!level.hasChunkAt(target)) {
                    continue;
                }
                BlockState state = level.getBlockState(target);
                if (!(state.getBlock() instanceof DeadFoliage foliage)) {
                    continue;
                }
                BlockState living = foliage.revived(state);
                if (living != null) {
                    level.setBlock(target, living, Block.UPDATE_ALL);
                    revived++;
                }
            }
        }
        if (revived > 0) {
            Nabu.LOGGER.debug("Greening layer {} revived {} block(s).", greenSweepY, revived);
        }

        greenSweepY++;
        if (greenSweepY > GREEN_HEIGHT) {
            greenSweepY = GREEN_IDLE;
            greened = true;
            setChanged();
            Nabu.LOGGER.info("Greening complete at {}.", pos);
        }
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

    /**
     * Claim beds a worldgen marker flagged for us.
     *
     * <p>Jigsaw pieces are placed chunk by chunk, so bed pieces routinely land after the shrine
     * does. Rather than have markers chase a controller that may not exist yet, the shrine keeps
     * looking until it finds its beds and then stops.
     */
    private void adoptFlaggedBeds(Level level, BlockPos pos) {
        int adopted = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-REACH_HORIZONTAL, -REACH_VERTICAL, -REACH_HORIZONTAL),
                pos.offset(REACH_HORIZONTAL, REACH_VERTICAL, REACH_HORIZONTAL))) {
            if (!(level.getBlockState(candidate).getBlock() instanceof PlantingBedBlock)
                    || !(level.getBlockEntity(candidate) instanceof PlantingBedBlockEntity bed)) {
                continue;
            }
            // Only marker-flagged beds, and only ones nobody has claimed yet.
            if (!bed.isFlagged() || bed.isWonderBed()) {
                continue;
            }

            BlockPos bedPos = candidate.immutable();
            bed.linkTo(pos, bed.terrace());
            registerBed(bed.terrace(), bedPos);
            adopted++;

            if (PlantingBedBlock.tierAt(level, bedPos) == BedTier.BOOSTED) {
                bed.reportBoosted(level);
            }
        }
        if (adopted > 0) {
            Nabu.LOGGER.info("Shrine at {} adopted {} flagged bed(s).", pos, adopted);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GardenControllerBlockEntity garden) {
        long time = level.getGameTime();

        // Ahead of the aura's interval gate below, since both of these need every tick.
        if (garden.awakenTicks != AWAKEN_IDLE) {
            garden.tickAwakening(level, pos);
        }
        if (garden.greenSweepY != GREEN_IDLE) {
            garden.tickGreenSweep(level, pos);
        }

        if (garden.known.isEmpty() && time % ADOPT_INTERVAL_TICKS == 0L) {
            garden.adoptFlaggedBeds(level, pos);
        }

        if (time % AURA_INTERVAL_TICKS != 0L) {
            return;
        }

        // Read once: the aura, the block state and the greening gate all want the same number.
        int live = garden.liveBoostedBeds();

        boolean powered = live > 0;
        if (state.getValue(GardenControllerBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(GardenControllerBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (powered && level instanceof ServerLevel server) {
            garden.radiate(server, pos);
        }

        // Every registered bed boosted at once, not merely one per terrace. Stricter than the
        // completion latch on purpose, and read live, so this is the garden genuinely running at
        // full flow rather than a record that it once did.
        if (!garden.greened && garden.greenSweepY == GREEN_IDLE
                && !garden.beds.isEmpty() && live == garden.beds.size()) {
            garden.greenSweepY = -GREEN_HEIGHT;
            Nabu.LOGGER.info("All {} bed(s) boosted at {}; greening the ruin.", live, pos);
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
        output.putBoolean("greened", greened);
        output.putIntArray("known", known.stream().mapToInt(Integer::intValue).toArray());
        output.putIntArray("restored", restored.stream().mapToInt(Integer::intValue).toArray());
        output.store("beds", BlockPos.CODEC.listOf(), List.copyOf(beds));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        completed = input.getBooleanOr("completed", false);
        greened = input.getBooleanOr("greened", false);
        known.clear();
        restored.clear();
        beds.clear();
        input.getIntArray("known").ifPresent(values -> Arrays.stream(values).boxed().forEach(known::add));
        input.getIntArray("restored").ifPresent(values -> Arrays.stream(values).boxed().forEach(restored::add));
        input.read("beds", BlockPos.CODEC.listOf()).ifPresent(beds::addAll);
    }
}
