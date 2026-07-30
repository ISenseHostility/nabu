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
import ai.jarno.nabu.registry.NabuTags;
import ai.jarno.nabu.registry.NabuTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
     * Reach for finding beds, measured from an edge rather than from the middle.
     *
     * <p>The shrine stands in the reservoir court on the north face, so the near lip of the
     * monument is three blocks away and the far one thirty; a radius sized for a centred origin
     * silently stops covering the far side. Sized to span the whole 33-block footprint from that
     * corner with room to spare, and to look from the court floor up past the summit deck.
     *
     * <p>Deliberately generous. The old pair cleared the beds that exist today by a single
     * block, which is not a margin -- it is a coincidence that moving one terrace would end.
     */
    public static final int REACH_HORIZONTAL = 32;
    public static final int REACH_VERTICAL = 24;

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
     * Reach of the greening sweep. Far wider than the aura, because the dead growth dresses the
     * whole ruin rather than clustering on the terraces the shrine waters -- and the shrine
     * stands in the reservoir court at one corner of the monument rather than at its middle, so
     * the radius has to cover the full 33-block footprint measured from an off-centre origin,
     * plus a margin of surrounding ground for anything the player has dressed themselves.
     */
    private static final int GREEN_RADIUS = 40;
    private static final int GREEN_HEIGHT = 24;
    private static final int GREEN_IDLE = Integer.MIN_VALUE;

    /** Patches bone mealed per tick. Four keeps the burst well under one sweep layer's cost. */
    private static final int BLOOM_PER_TICK = 4;

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

    /** Greened but not yet bloomed patches. Transient by design -- see {@link #tickBloom}. */
    private final ArrayDeque<BlockPos> pendingBloom = new ArrayDeque<>();

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
        int y = pos.getY() + greenSweepY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // The chunk lookup, not the block read, is what a layer this wide would otherwise spend
        // its time on. Walking dz on the inside means sixteen consecutive columns share a chunk,
        // so the check is hoisted and only repeated when the cursor actually crosses a border.
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean loaded = false;

        for (int dx = -GREEN_RADIUS; dx <= GREEN_RADIUS; dx++) {
            for (int dz = -GREEN_RADIUS; dz <= GREEN_RADIUS; dz++) {
                cursor.set(pos.getX() + dx, y, pos.getZ() + dz);
                int chunkX = cursor.getX() >> 4;
                int chunkZ = cursor.getZ() >> 4;
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    // Never force-load: growth in unloaded chunks simply stays dead.
                    loaded = level.hasChunkAt(cursor);
                }
                if (!loaded) {
                    continue;
                }
                BlockState state = level.getBlockState(cursor);
                if (state.getBlock() instanceof DeadFoliage foliage) {
                    BlockState living = foliage.revived(state);
                    if (living != null) {
                        // The cursor is reused every column, so the world must not keep it.
                        level.setBlock(cursor.immutable(), living, Block.UPDATE_ALL);
                        revived++;
                    }
                } else if (state.is(NabuTags.GREENS_INTO_GRASS) && greenGround(level, cursor.immutable())) {
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
     * Turn a drift of bare earth to grass and queue it to be bone mealed.
     *
     * <p>The bone meal is the shrine's work rather than the fertility aura's on purpose: the aura
     * reaches barely a third as far as the sweep and spends four attempts a second across that
     * whole volume, so the far side of the monument would turn green long before anything grew on
     * it.
     *
     * <p>It is queued rather than done here because {@code GrassBlock.performBonemeal} makes a
     * hundred and twenty-eight placement attempts per call. A layer of the sweep can hold dozens
     * of patches, and firing them all on one tick is exactly the hitch the sliced sweep exists to
     * avoid. {@link #tickBloom} drains them a few at a time instead.
     *
     * @return true, so the caller can count it; the conversion cannot fail once the tag matched
     */
    private boolean greenGround(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        pendingBloom.add(pos);
        return true;
    }

    /**
     * Bring a few queued patches into flower.
     *
     * <p>Routed through {@link BonemealableBlock} rather than placing vegetation directly, so each
     * patch grows whatever the biome it stands in would actually put there. The state is re-read
     * rather than trusted from when it was queued: a player may have dug the grass out in the
     * seconds since, and it is not ours to grow anything on what replaced it.
     *
     * <p>The queue is deliberately not persisted. It is cosmetic polish trailing a few seconds
     * behind the green, and a server stopped mid-bloom leaves plain grass rather than anything
     * broken -- whereas saving it would mean reasoning about a queue whose blocks may no longer
     * exist on load.
     */
    private void tickBloom(ServerLevel level) {
        for (int i = 0; i < BLOOM_PER_TICK && !pendingBloom.isEmpty(); i++) {
            BlockPos pos = pendingBloom.poll();
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, pos, state)) {
                bonemealable.performBonemeal(level, level.getRandom(), pos, state);
            }
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
     * <p>Jigsaw pieces are placed chunk by chunk and chunks arrive in whatever order the player
     * walks, so bed pieces routinely land -- or become readable to us -- after the shrine does.
     * Rather than have markers chase a controller that may not exist yet, the shrine looks for
     * them itself.
     *
     * <p>It keeps looking indefinitely rather than stopping at the first pass that finds
     * anything. That gate was the bug: a pass that ran while one corner of the monument was
     * still unloaded latched a partial roster of terraces, and every question downstream of it
     * -- which terraces exist, and therefore when the Wonder is complete -- was answered against
     * that partial view forever after. Repeating is affordable because the scan walks the block
     * entities each chunk already keeps rather than every block in a 65x49x65 box, and it is
     * harmless because only a worldgen marker can flag a bed: a shrine a player raises in their
     * own base walks a few map entries every five seconds and adopts nothing, ever.
     */
    private void adoptFlaggedBeds(ServerLevel level, BlockPos pos) {
        List<PlantingBedBlockEntity> adopted = new ArrayList<>();
        for (PlantingBedBlockEntity bed : bedsInReach(level, pos)) {
            // Only marker-flagged beds, and only ones nobody has claimed yet.
            if (!bed.isFlagged() || bed.isWonderBed()) {
                continue;
            }
            bed.linkTo(pos, bed.terrace());
            registerBed(bed.terrace(), bed.getBlockPos());
            adopted.add(bed);
        }

        if (adopted.isEmpty()) {
            return;
        }
        Nabu.LOGGER.info("Shrine at {} adopted {} flagged bed(s); {} terrace(s) known.",
                pos, adopted.size(), known.size());

        // Reported only once every bed in this pass is registered. Inline, the first already
        // boosted bed would hand checkCompletion a `known` set that was still filling up, and it
        // would latch the whole Wonder complete off whichever terrace happened to be walked
        // first.
        for (PlantingBedBlockEntity bed : adopted) {
            if (PlantingBedBlock.tierAt(level, bed.getBlockPos()) == BedTier.BOOSTED) {
                bed.reportBoosted(level);
            }
        }
    }

    /**
     * Every planting bed within reach of {@code pos}, in chunks that happen to be loaded.
     *
     * <p>Walks the block entities each chunk already keeps rather than reading every block in a
     * 65x49x65 box -- a couple of hundred thousand lookups against a few dozen map entries --
     * which is what makes a repeating scan affordable at all. It never force-loads either: a
     * chunk that has not arrived is simply absent from the answer, rather than generated from
     * inside a block-entity tick.
     */
    public static List<PlantingBedBlockEntity> bedsInReach(ServerLevel level, BlockPos pos) {
        List<PlantingBedBlockEntity> found = new ArrayList<>();
        int minChunkX = (pos.getX() - REACH_HORIZONTAL) >> 4;
        int maxChunkX = (pos.getX() + REACH_HORIZONTAL) >> 4;
        int minChunkZ = (pos.getZ() - REACH_HORIZONTAL) >> 4;
        int maxChunkZ = (pos.getZ() + REACH_HORIZONTAL) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof PlantingBedBlockEntity bed
                            && inReach(pos, blockEntity.getBlockPos())) {
                        found.add(bed);
                    }
                }
            }
        }
        return found;
    }

    private static boolean inReach(BlockPos shrine, BlockPos bed) {
        return Math.abs(bed.getX() - shrine.getX()) <= REACH_HORIZONTAL
                && Math.abs(bed.getY() - shrine.getY()) <= REACH_VERTICAL
                && Math.abs(bed.getZ() - shrine.getZ()) <= REACH_HORIZONTAL;
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
        // Trails the sweep rather than gating on it, so the bloom keeps rolling out over the
        // patches the sweep has already turned while it is still climbing.
        if (!garden.pendingBloom.isEmpty() && level instanceof ServerLevel server) {
            garden.tickBloom(server);
        }

        if (time % ADOPT_INTERVAL_TICKS == 0L && level instanceof ServerLevel server) {
            garden.adoptFlaggedBeds(server, pos);
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
