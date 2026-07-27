# Ancient Crops (Judean Date + Emmer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two extinct crops to Nabu — Judean Date, a two-block-tall crop gated on the full bed ladder, and Emmer, which regrows on right-click harvest and self-seeds onto neighbouring beds.

**Architecture:** Both crops live entirely in `common`; nothing here is loader-divergent, so `fabric/` and `neoforge/` are untouched. A new `CropGates` utility holds the two soil checks all three extinct crops share. Emmer extends the existing `ExtinctCropBlock` (inheriting its fruiting gate unchanged); Judean Date extends vanilla `DoublePlantBlock` and implements `BonemealableBlock`, modelled on `PitcherCropBlock` but not extending it.

**Tech Stack:** Java 25, Minecraft 26.2 (official Mojang names, de-obfuscated), Architectury API 21.0.4, Architectury Loom (no-remap), Gradle.

**Spec:** `docs/superpowers/specs/2026-07-27-ancient-crops-design.md`

## Global Constraints

- **All gameplay logic lives in `common`.** Platform modules hold only hooks. Nothing in this plan touches `fabric/` or `neoforge/`.
- **Both loaders must stay in lockstep.** A task is not done until `./gradlew build` succeeds, which builds both.
- **MC 26.2 is de-obfuscated** — use official Mojang names throughout. No Yarn/MCP/SRG names.
- **Never assume API shapes from memory.** Every signature used in this plan was verified against `minecraft-merged-deobf-26.2.jar` with `javap`. If something does not compile, check the jar before changing the design.
- **Extinct crops grow anywhere but only *fruit* when Boosted.** Growth and fruiting are separate checks. Every growth path — random tick *and* bone meal — must funnel through the same cap method, or bone meal becomes a way to skip the puzzle.
- **No world scans in a tick loop.** Emmer's spread tests one random candidate position, never a box scan.
- **No test harness exists in this repo.** Verification per task is `./gradlew build` plus commit. In-world behaviour is verified by Jarno using the checklist at the end of this plan — do not claim behaviour is verified, only that it compiles.
- **Art is Jarno's.** All models here point at vanilla placeholder textures, exactly as `silphium_stage0.json` already points at `minecraft:block/wheat_stage0`. Do not generate PNGs.
- Registration idiom: every block/item registered through `DeferredRegister` needs `.setId(Nabu.key(Registries.BLOCK|ITEM, "<name>"))`, and every item uses the private `tabbed()` helper in `NabuItems` so it lands in the creative tab.

---

## File Structure

**Created:**

| File | Responsibility |
| --- | --- |
| `common/src/main/java/ai/jarno/nabu/block/CropGates.java` | The two soil checks shared by all extinct crops |
| `common/src/main/java/ai/jarno/nabu/block/EmmerBlock.java` | Emmer: harvest-and-regrow, self-seeding |
| `common/src/main/java/ai/jarno/nabu/block/JudeanDateBlock.java` | Judean Date: two-block growth, three-rung gate |
| `common/src/main/resources/assets/nabu/blockstates/{emmer,judean_date}.json` | State → model mapping |
| `common/src/main/resources/assets/nabu/models/block/emmer_stage0..7.json` | 8 crop stage models |
| `common/src/main/resources/assets/nabu/models/block/judean_date_*.json` | 8 stage models (2 single + 3×2 halves) |
| `common/src/main/resources/assets/nabu/items/*.json`, `models/item/*.json` | 4 item models |
| `common/src/main/resources/data/nabu/loot_table/blocks/{emmer,judean_date}.json` | Block drops |

**Modified:**

| File | Change |
| --- | --- |
| `common/src/main/java/ai/jarno/nabu/block/ExtinctCropBlock.java` | Widen `codec()`, delegate boost check to `CropGates` |
| `common/src/main/java/ai/jarno/nabu/registry/NabuBlocks.java` | +2 block entries |
| `common/src/main/java/ai/jarno/nabu/registry/NabuItems.java` | +4 item entries |
| `common/src/main/resources/assets/nabu/lang/en_us.json` | +6 translation keys |
| `common/src/main/resources/data/nabu/loot_table/chests/hanging_gardens.json` | +2 seed pools |

---

## Task 1: Shared soil gates

Extract the checks both new crops need. `ExtinctCropBlock` holds the boost check privately today, and `JudeanDateBlock` is not a `CropBlock`, so it cannot inherit it. Pure refactor — no behaviour change.

**Files:**
- Create: `common/src/main/java/ai/jarno/nabu/block/CropGates.java`
- Modify: `common/src/main/java/ai/jarno/nabu/block/ExtinctCropBlock.java` (lines 33-50)

**Interfaces:**
- Consumes: `PlantingBedBlock.tierAt(BlockGetter, BlockPos)` and `BedTier.BOOSTED`, both existing.
- Produces:
  - `CropGates.isBoosted(LevelReader level, BlockPos cropPos) -> boolean`
  - `CropGates.isMoist(LevelReader level, BlockPos cropPos) -> boolean`
  - `ExtinctCropBlock.codec()` widened to `MapCodec<? extends ExtinctCropBlock>`, making the class subclassable.

Both gate methods take the position of the **crop**, not the soil; they look down one block themselves.

- [ ] **Step 1: Create `CropGates`**

```java
package ai.jarno.nabu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The soil checks the extinct crops share.
 *
 * <p>Both take the position of the <em>crop</em> and look down one block themselves, so a
 * caller never has to remember which of the two positions a gate wants.
 */
public final class CropGates {
    private CropGates() {
    }

    /** Whether the soil under this crop is a planting bed currently running at BOOSTED. */
    public static boolean isBoosted(LevelReader level, BlockPos cropPos) {
        return PlantingBedBlock.tierAt(level, cropPos.below()) == BedTier.BOOSTED;
    }

    /**
     * Whether the soil under this crop holds any moisture at all.
     *
     * <p>Reads MOISTURE rather than {@link BedTier} on purpose: vanilla farmland carries the
     * same property, so ordinary moist farmland counts exactly like a Watered bed and the
     * crops stay plantable outside the Wonder. Only a bone-dry bed reads false.
     */
    public static boolean isMoist(LevelReader level, BlockPos cropPos) {
        BlockState soil = level.getBlockState(cropPos.below());
        return soil.hasProperty(BlockStateProperties.MOISTURE)
                && soil.getValue(BlockStateProperties.MOISTURE) > 0;
    }
}
```

- [ ] **Step 2: Widen `ExtinctCropBlock.codec()` and delegate the boost check**

In `ExtinctCropBlock.java`, replace lines 33-45 (the `codec()` method and the private `isBoosted` helper) with:

```java
    // Wildcard, not MapCodec<ExtinctCropBlock>: an invariant return type here would make this
    // class impossible to subclass, which is exactly why vanilla's PitcherCropBlock cannot be
    // extended.
    @Override
    public MapCodec<? extends ExtinctCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return NabuItems.SILPHIUM_SEEDS.get();
    }

    private static boolean isBoosted(LevelReader level, BlockPos pos) {
        return CropGates.isBoosted(level, pos);
    }
```

Leave the rest of the file untouched. `growthCap`, `randomTick`, `growCrops`, and `isValidBonemealTarget` all keep working through the now-delegating `isBoosted`.

- [ ] **Step 3: Verify it compiles on both loaders**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. This builds `common`, `fabric`, and `neoforge`.

- [ ] **Step 4: Commit**

```bash
git add common/src/main/java/ai/jarno/nabu/block/CropGates.java common/src/main/java/ai/jarno/nabu/block/ExtinctCropBlock.java
git commit -m "Share the extinct crops' soil checks so a tall crop can use them"
```

---

## Task 2: Emmer

The self-planting crop. Extends `ExtinctCropBlock`, so the fruiting gate (age 7 only on a Boosted bed) is inherited unchanged; only the seed item differs. Two behaviours are added on top.

**Files:**
- Create: `common/src/main/java/ai/jarno/nabu/block/EmmerBlock.java`
- Modify: `common/src/main/java/ai/jarno/nabu/registry/NabuBlocks.java`
- Modify: `common/src/main/java/ai/jarno/nabu/registry/NabuItems.java`
- Create: `common/src/main/resources/assets/nabu/blockstates/emmer.json`
- Create: `common/src/main/resources/assets/nabu/models/block/emmer_stage0.json` … `emmer_stage7.json`
- Create: `common/src/main/resources/assets/nabu/items/emmer.json`, `items/emmer_seeds.json`
- Create: `common/src/main/resources/assets/nabu/models/item/emmer.json`, `models/item/emmer_seeds.json`
- Create: `common/src/main/resources/data/nabu/loot_table/blocks/emmer.json`
- Modify: `common/src/main/resources/assets/nabu/lang/en_us.json`
- Modify: `common/src/main/resources/data/nabu/loot_table/chests/hanging_gardens.json`

**Interfaces:**
- Consumes: `ExtinctCropBlock` (now subclassable, from Task 1); `NabuItems.EMMER`, `NabuItems.EMMER_SEEDS` (created in this task); `PlantingBedBlock` for the spread target check.
- Produces: `NabuBlocks.EMMER` of type `RegistrySupplier<EmmerBlock>`; `NabuItems.EMMER` and `NabuItems.EMMER_SEEDS`, both `RegistrySupplier<Item>`.

- [ ] **Step 1: Create `EmmerBlock`**

```java
package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Emmer -- the ancient wheat of Mesopotamia, and the one crop that plants itself.
 *
 * <p>The fruiting gate is inherited untouched: it only reaches its final age on a
 * {@link BedTier#BOOSTED} bed. Everything below hangs off that, so neither the regrowing
 * harvest nor the self-seeding needs a boost check of its own -- reaching max age already
 * proves the bed was boosted.
 */
public class EmmerBlock extends ExtinctCropBlock {
    public static final MapCodec<EmmerBlock> CODEC = simpleCodec(EmmerBlock::new);

    /**
     * Age a harvested head falls back to. Three growth steps short of fruiting again, so
     * re-harvesting beats replanting from seed without being free.
     */
    private static final int REGROWN_AGE = 4;

    /** One in this many random ticks on a mature plant attempts a spread. */
    private static final int SPREAD_ODDS = 10;

    private static final int SPREAD_RADIUS = 2;
    private static final int SPREAD_HEIGHT = 1;

    public EmmerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends EmmerBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return NabuItems.EMMER_SEEDS.get();
    }

    /**
     * Pick the grain by hand and leave the plant standing.
     *
     * <p>Only offered at full growth, which is boosted-only, so this is a reward for the
     * irrigation rather than a way around it. Breaking the block by hand still yields seeds,
     * which stays the way to get planting stock.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!isMaxAge(state)) {
            // Not ripe: fall through to normal interaction rather than eating the click.
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel server) {
            RandomSource random = server.random;
            popResource(server, pos, new ItemStack(NabuItems.EMMER.get(), 1 + random.nextInt(2)));
            server.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                    1.0F, 0.8F + random.nextFloat() * 0.4F);
            server.setBlock(pos, getStateForAge(REGROWN_AGE), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Checked before super, which cannot change a max-age plant anyway -- it returns
        // early once the age is at its cap.
        if (isMaxAge(state) && random.nextInt(SPREAD_ODDS) == 0) {
            trySpread(level, pos, random);
        }
        super.randomTick(state, level, pos, random);
    }

    /**
     * Sow one seedling nearby, maybe.
     *
     * <p>Tests a single random candidate rather than scanning the box: this runs on every
     * mature plant, so it has to stay O(1). The cost is that spreading is probabilistic and
     * will not reliably fill the nearest gap, which is the right trade here.
     *
     * <p>Planting beds only. Emmer must never creep into a player's own farmland.
     */
    private void trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos target = pos.offset(
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS,
                random.nextInt(SPREAD_HEIGHT * 2 + 1) - SPREAD_HEIGHT,
                random.nextInt(SPREAD_RADIUS * 2 + 1) - SPREAD_RADIUS);

        if (target.equals(pos) || !level.hasChunkAt(target)) {
            return;
        }
        if (!level.isEmptyBlock(target)) {
            return;
        }
        if (!(level.getBlockState(target.below()).getBlock() instanceof PlantingBedBlock)) {
            return;
        }

        level.setBlock(target, getStateForAge(0), Block.UPDATE_CLIENTS);
    }
}
```

- [ ] **Step 2: Register the block**

In `NabuBlocks.java`, add the import `ai.jarno.nabu.block.EmmerBlock` and insert after the `SILPHIUM` entry:

```java
    public static final RegistrySupplier<EmmerBlock> EMMER = BLOCKS.register(
            "emmer",
            () -> new EmmerBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.WHEAT)
                    .setId(Nabu.key(Registries.BLOCK, "emmer"))));
```

Note `ofLegacyCopy(Blocks.WHEAT)` without `.randomTicks()` — matching `SILPHIUM`. `CropBlock` overrides `isRandomlyTicking(BlockState)` to key off age, and that override wins over the properties flag, so the crop still ticks.

- [ ] **Step 3: Register the items**

In `NabuItems.java`, insert after the `SILPHIUM` entry:

```java
    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> EMMER_SEEDS = ITEMS.register(
            "emmer_seeds",
            () -> new BlockItem(
                    NabuBlocks.EMMER.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "emmer_seeds"))));

    /** The grain itself -- reserved as a crafting ingredient, no use yet. */
    public static final RegistrySupplier<Item> EMMER = ITEMS.register(
            "emmer",
            () -> new Item(tabbed().setId(Nabu.key(Registries.ITEM, "emmer"))));
```

- [ ] **Step 4: Create the blockstate**

`common/src/main/resources/assets/nabu/blockstates/emmer.json`:

```json
{
  "variants": {
    "age=0": { "model": "nabu:block/emmer_stage0" },
    "age=1": { "model": "nabu:block/emmer_stage1" },
    "age=2": { "model": "nabu:block/emmer_stage2" },
    "age=3": { "model": "nabu:block/emmer_stage3" },
    "age=4": { "model": "nabu:block/emmer_stage4" },
    "age=5": { "model": "nabu:block/emmer_stage5" },
    "age=6": { "model": "nabu:block/emmer_stage6" },
    "age=7": { "model": "nabu:block/emmer_stage7" }
  }
}
```

- [ ] **Step 5: Create the 8 stage models**

Create `common/src/main/resources/assets/nabu/models/block/emmer_stageN.json` for N = 0..7, each with its matching wheat texture. For N = 0:

```json
{
  "parent": "minecraft:block/crop",
  "textures": {
    "crop": "minecraft:block/wheat_stage0"
  }
}
```

Repeat for 1 through 7, changing both the filename and `wheat_stage0` → `wheat_stageN`. These are placeholders matching how `silphium_stage0.json` already borrows `wheat_stage0`.

- [ ] **Step 6: Create the item models**

`assets/nabu/items/emmer_seeds.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "nabu:item/emmer_seeds"
  }
}
```

`assets/nabu/items/emmer.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "nabu:item/emmer"
  }
}
```

`assets/nabu/models/item/emmer_seeds.json` — beetroot seeds as the placeholder so Emmer seeds are visually distinct from Silphium's in the creative tab:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/beetroot_seeds"
  }
}
```

`assets/nabu/models/item/emmer.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/wheat"
  }
}
```

The grain shares Silphium's `wheat` placeholder — they are told apart by their tooltip names until Jarno's art lands.

- [ ] **Step 7: Create the loot table**

`common/src/main/resources/data/nabu/loot_table/blocks/emmer.json` — same shape as `silphium.json`:

```json
{
  "type": "minecraft:block",
  "functions": [
    {
      "function": "minecraft:explosion_decay"
    }
  ],
  "pools": [
    {
      "rolls": 1.0,
      "entries": [
        {
          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "name": "nabu:emmer",
              "conditions": [
                {
                  "condition": "minecraft:block_state_property",
                  "block": "nabu:emmer",
                  "properties": {
                    "age": "7"
                  }
                }
              ]
            },
            {
              "type": "minecraft:item",
              "name": "nabu:emmer_seeds"
            }
          ]
        }
      ]
    },
    {
      "rolls": 1.0,
      "conditions": [
        {
          "condition": "minecraft:block_state_property",
          "block": "nabu:emmer",
          "properties": {
            "age": "7"
          }
        }
      ],
      "entries": [
        {
          "type": "minecraft:item",
          "name": "nabu:emmer_seeds",
          "functions": [
            {
              "function": "minecraft:apply_bonus",
              "enchantment": "minecraft:fortune",
              "formula": "minecraft:binomial_with_bonus_count",
              "parameters": {
                "extra": 3,
                "probability": 0.5714286
              }
            }
          ]
        }
      ]
    }
  ],
  "random_sequence": "nabu:blocks/emmer"
}
```

- [ ] **Step 8: Add the chest pool**

In `data/nabu/loot_table/chests/hanging_gardens.json`, insert a new pool after the existing `nabu:silphium_seeds` pool (i.e. after the object closing at line 41, before the bulk-loot pool):

```json
    {
      "rolls": 1.0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "nabu:emmer_seeds",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": {
                "type": "minecraft:uniform",
                "min": 2.0,
                "max": 4.0
              }
            }
          ]
        }
      ]
    },
```

- [ ] **Step 9: Add translations**

In `assets/nabu/lang/en_us.json`, add `"block.nabu.emmer": "Emmer"` after the `block.nabu.silphium` line, and after the `item.nabu.silphium` line:

```json
  "item.nabu.emmer_seeds": "Emmer Seeds",
  "item.nabu.emmer": "Emmer Grain",
```

- [ ] **Step 10: Verify it compiles on both loaders**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add common/src/main/java/ai/jarno/nabu/block/EmmerBlock.java common/src/main/java/ai/jarno/nabu/registry common/src/main/resources
git commit -m "Add emmer, which regrows when picked and seeds the beds around it"
```

---

## Task 3: Judean Date

The two-block crop. Extends `DoublePlantBlock` and implements `BonemealableBlock`, modelled on vanilla's `PitcherCropBlock` — which **cannot** be extended, because its `codec()` is declared `MapCodec<PitcherCropBlock>` (invariant) and its growth helpers are all `private`.

**Files:**
- Create: `common/src/main/java/ai/jarno/nabu/block/JudeanDateBlock.java`
- Modify: `common/src/main/java/ai/jarno/nabu/registry/NabuBlocks.java`
- Modify: `common/src/main/java/ai/jarno/nabu/registry/NabuItems.java`
- Create: `common/src/main/resources/assets/nabu/blockstates/judean_date.json`
- Create: `common/src/main/resources/assets/nabu/models/block/judean_date_*.json` (8 files)
- Create: `common/src/main/resources/assets/nabu/items/judean_date.json`, `items/judean_date_seeds.json`
- Create: `common/src/main/resources/assets/nabu/models/item/judean_date.json`, `models/item/judean_date_seeds.json`
- Create: `common/src/main/resources/data/nabu/loot_table/blocks/judean_date.json`
- Modify: `common/src/main/resources/assets/nabu/lang/en_us.json`
- Modify: `common/src/main/resources/data/nabu/loot_table/chests/hanging_gardens.json`

**Interfaces:**
- Consumes: `CropGates.isBoosted` / `CropGates.isMoist` from Task 1; `NabuItems.JUDEAN_DATE_SEEDS` (created here).
- Produces: `NabuBlocks.JUDEAN_DATE` of type `RegistrySupplier<JudeanDateBlock>`; `NabuItems.JUDEAN_DATE` and `NabuItems.JUDEAN_DATE_SEEDS`, both `RegistrySupplier<Item>`.

The age cap, which every growth path funnels through:

| Soil beneath | Cap | Result |
| --- | --- | --- |
| Dry bed | 1 | Stunted single-block sprout |
| Watered/Boosted bed, or moist farmland | 3 | Full two-block palm, bare |
| Boosted bed | 4 | Two blocks, fruiting |

- [ ] **Step 1: Create `JudeanDateBlock`**

```java
package ai.jarno.nabu.block;

import ai.jarno.nabu.registry.NabuItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Judean date palm -- extinct since antiquity, and the only crop here that stands two
 * blocks tall.
 *
 * <p>Alone among the extinct crops it reads the whole bed ladder rather than just the top
 * rung. On a bone-dry bed it never straightens up at all; once merely watered it reaches
 * full height but stays barren; only a {@link BedTier#BOOSTED} bed carries it the last step
 * into fruit. Height and fruit are two separate rewards for two separate rungs.
 *
 * <p>Modelled on vanilla's {@code PitcherCropBlock} but deliberately not extending it: that
 * class declares {@code codec()} as an invariant {@code MapCodec<PitcherCropBlock>}, so no
 * subclass can legally override it, and all of its growth helpers are private.
 */
public class JudeanDateBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final MapCodec<JudeanDateBlock> CODEC = simpleCodec(JudeanDateBlock::new);

    public static final int MAX_AGE = 4;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_4;

    /** Age from which the palm stands two blocks tall. */
    private static final int DOUBLE_FROM = 2;

    /** One in this many random ticks advances the plant. */
    private static final int GROWTH_ODDS = 6;

    private static final VoxelShape SHAPE_SPROUT = Block.column(12.0, 0.0, 6.0);
    private static final VoxelShape SHAPE_STALK = Block.column(12.0, 0.0, 16.0);

    public JudeanDateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public MapCodec<? extends JudeanDateBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, HALF);
    }

    // --- placement -------------------------------------------------------------------
    // DoublePlantBlock assumes a plant that is two blocks tall the moment it is placed.
    // This one is not: it starts as a single-block sprout and only grows its top half at
    // DOUBLE_FROM. Both of the following must be overridden, or planting a seed leaves a
    // floating orphan top block.

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        // Intentionally empty: no upper half exists yet. Do not call super.
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_CROPS);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AGE) < DOUBLE_FROM ? SHAPE_SPROUT : SHAPE_STALK;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(NabuItems.JUDEAN_DATE_SEEDS.get());
    }

    // --- growth ----------------------------------------------------------------------

    /**
     * Highest age reachable here. Dry soil stops it just short of standing up, moist soil
     * just short of fruiting, and only a boosted bed lets it finish.
     */
    private int growthCap(LevelReader level, BlockPos lowerPos) {
        if (CropGates.isBoosted(level, lowerPos)) {
            return MAX_AGE;
        }
        if (CropGates.isMoist(level, lowerPos)) {
            return MAX_AGE - 1;
        }
        return DOUBLE_FROM - 1;
    }

    /** Only the lower half ticks; it drives both. Otherwise the halves race and double-grow. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }

        int age = state.getValue(AGE);
        if (age >= growthCap(level, pos) || !hasRoomToReach(level, pos, age, age + 1)) {
            return;
        }

        if (random.nextInt(GROWTH_ODDS) == 0) {
            grow(level, pos, age + 1);
        }
    }

    /**
     * Whether the step from {@code from} to {@code to} is physically possible.
     *
     * <p>Only the single-to-double transition needs space: past that the upper half is
     * already ours and standing there. A palm under a ceiling stalls rather than eating the
     * block above it.
     */
    private static boolean hasRoomToReach(LevelReader level, BlockPos lowerPos, int from, int to) {
        return from >= DOUBLE_FROM || to < DOUBLE_FROM || level.isEmptyBlock(lowerPos.above());
    }

    /** Write the new age to both halves at once, creating the upper one if it is time. */
    private void grow(ServerLevel level, BlockPos lowerPos, int age) {
        level.setBlock(lowerPos,
                defaultBlockState().setValue(AGE, age).setValue(HALF, DoubleBlockHalf.LOWER),
                Block.UPDATE_CLIENTS);
        if (age >= DOUBLE_FROM) {
            level.setBlock(lowerPos.above(),
                    defaultBlockState().setValue(AGE, age).setValue(HALF, DoubleBlockHalf.UPPER),
                    Block.UPDATE_CLIENTS);
        }
    }

    /** Bone meal may be applied to either half; growth always happens from the lower one. */
    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // --- bone meal -------------------------------------------------------------------
    // Routed through growthCap like everything else. Bone meal must never be a way around
    // the irrigation.

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos lower = lowerPos(state, pos);
        int age = state.getValue(AGE);
        return age < growthCap(level, lower) && hasRoomToReach(level, lower, age, age + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos lower = lowerPos(state, pos);
        int age = state.getValue(AGE);
        int grown = Math.min(growthCap(level, lower), age + 1);
        if (grown > age && hasRoomToReach(level, lower, age, grown)) {
            grow(level, lower, grown);
        }
    }
}
```

- [ ] **Step 2: Register the block**

In `NabuBlocks.java`, add the import `ai.jarno.nabu.block.JudeanDateBlock` and insert after the `EMMER` entry:

```java
    public static final RegistrySupplier<JudeanDateBlock> JUDEAN_DATE = BLOCKS.register(
            "judean_date",
            () -> new JudeanDateBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.WHEAT)
                    .setId(Nabu.key(Registries.BLOCK, "judean_date"))));
```

- [ ] **Step 3: Register the items**

In `NabuItems.java`, add the imports `net.minecraft.world.food.FoodProperties` and insert after the `EMMER` entry:

```java
    /** Plantable seed. Placing it sows the crop. */
    public static final RegistrySupplier<Item> JUDEAN_DATE_SEEDS = ITEMS.register(
            "judean_date_seeds",
            () -> new BlockItem(
                    NabuBlocks.JUDEAN_DATE.get(),
                    tabbed().setId(Nabu.key(Registries.ITEM, "judean_date_seeds"))));

    /**
     * The harvest itself -- only obtainable from a palm that reached the fruiting stage.
     * Apple-tier hunger with much better saturation, as a dense dried fruit should be.
     */
    public static final RegistrySupplier<Item> JUDEAN_DATE = ITEMS.register(
            "judean_date",
            () -> new Item(tabbed()
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.5F)
                            .build())
                    .setId(Nabu.key(Registries.ITEM, "judean_date"))));
```

- [ ] **Step 4: Create the blockstate**

`assets/nabu/blockstates/judean_date.json`. All ten `age` × `half` combinations must be present or the game logs a missing-model error; ages 0–1 with `half=upper` are never placed in-world and point at the stage-0 model as a stub.

```json
{
  "variants": {
    "age=0,half=lower": { "model": "nabu:block/judean_date_stage0" },
    "age=0,half=upper": { "model": "nabu:block/judean_date_stage0" },
    "age=1,half=lower": { "model": "nabu:block/judean_date_stage1" },
    "age=1,half=upper": { "model": "nabu:block/judean_date_stage0" },
    "age=2,half=lower": { "model": "nabu:block/judean_date_stage2_bottom" },
    "age=2,half=upper": { "model": "nabu:block/judean_date_stage2_top" },
    "age=3,half=lower": { "model": "nabu:block/judean_date_stage3_bottom" },
    "age=3,half=upper": { "model": "nabu:block/judean_date_stage3_top" },
    "age=4,half=lower": { "model": "nabu:block/judean_date_stage4_bottom" },
    "age=4,half=upper": { "model": "nabu:block/judean_date_stage4_top" }
  }
}
```

- [ ] **Step 5: Create the 8 stage models**

All use `minecraft:block/crop` (a cross model) with pitcher-crop placeholder textures. Create each file under `assets/nabu/models/block/` with the `crop` texture listed:

| File | `crop` texture |
| --- | --- |
| `judean_date_stage0.json` | `minecraft:block/pitcher_crop_bottom_stage_1` |
| `judean_date_stage1.json` | `minecraft:block/pitcher_crop_bottom_stage_1` |
| `judean_date_stage2_bottom.json` | `minecraft:block/pitcher_crop_bottom_stage_2` |
| `judean_date_stage2_top.json` | `minecraft:block/pitcher_crop_top` |
| `judean_date_stage3_bottom.json` | `minecraft:block/pitcher_crop_bottom_stage_3` |
| `judean_date_stage3_top.json` | `minecraft:block/pitcher_crop_top_stage_3` |
| `judean_date_stage4_bottom.json` | `minecraft:block/pitcher_crop_bottom_stage_4` |
| `judean_date_stage4_top.json` | `minecraft:block/pitcher_crop_top_stage_4` |

Vanilla ships no `pitcher_crop_bottom_stage_0` and no top texture below stage 3, hence the two reuses. Each file follows this shape — shown for `judean_date_stage0.json`:

```json
{
  "parent": "minecraft:block/crop",
  "textures": {
    "crop": "minecraft:block/pitcher_crop_bottom_stage_1"
  }
}
```

- [ ] **Step 6: Create the item models**

`assets/nabu/items/judean_date_seeds.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "nabu:item/judean_date_seeds"
  }
}
```

`assets/nabu/items/judean_date.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "nabu:item/judean_date"
  }
}
```

`assets/nabu/models/item/judean_date_seeds.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/pitcher_pod"
  }
}
```

`assets/nabu/models/item/judean_date.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/cocoa_beans"
  }
}
```

- [ ] **Step 7: Create the loot table**

`data/nabu/loot_table/blocks/judean_date.json`. Every pool is conditioned on `half=lower`, so the two halves cannot both drop:

```json
{
  "type": "minecraft:block",
  "functions": [
    {
      "function": "minecraft:explosion_decay"
    }
  ],
  "pools": [
    {
      "rolls": 1.0,
      "conditions": [
        {
          "condition": "minecraft:block_state_property",
          "block": "nabu:judean_date",
          "properties": {
            "half": "lower"
          }
        }
      ],
      "entries": [
        {
          "type": "minecraft:alternatives",
          "children": [
            {
              "type": "minecraft:item",
              "name": "nabu:judean_date",
              "conditions": [
                {
                  "condition": "minecraft:block_state_property",
                  "block": "nabu:judean_date",
                  "properties": {
                    "age": "4"
                  }
                }
              ]
            },
            {
              "type": "minecraft:item",
              "name": "nabu:judean_date_seeds"
            }
          ]
        }
      ]
    },
    {
      "rolls": 1.0,
      "conditions": [
        {
          "condition": "minecraft:block_state_property",
          "block": "nabu:judean_date",
          "properties": {
            "half": "lower",
            "age": "4"
          }
        }
      ],
      "entries": [
        {
          "type": "minecraft:item",
          "name": "nabu:judean_date_seeds",
          "functions": [
            {
              "function": "minecraft:apply_bonus",
              "enchantment": "minecraft:fortune",
              "formula": "minecraft:binomial_with_bonus_count",
              "parameters": {
                "extra": 3,
                "probability": 0.5714286
              }
            }
          ]
        }
      ]
    }
  ],
  "random_sequence": "nabu:blocks/judean_date"
}
```

- [ ] **Step 8: Add the chest pool**

In `data/nabu/loot_table/chests/hanging_gardens.json`, insert after the `nabu:emmer_seeds` pool added in Task 2:

```json
    {
      "rolls": 1.0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "nabu:judean_date_seeds",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": {
                "type": "minecraft:uniform",
                "min": 1.0,
                "max": 3.0
              }
            }
          ]
        }
      ]
    },
```

- [ ] **Step 9: Add translations**

In `assets/nabu/lang/en_us.json`, add `"block.nabu.judean_date": "Judean Date Palm"` after the `block.nabu.emmer` line, and after the `item.nabu.emmer` line:

```json
  "item.nabu.judean_date_seeds": "Judean Date Seeds",
  "item.nabu.judean_date": "Judean Date",
```

- [ ] **Step 10: Verify it compiles on both loaders**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add common/src/main/java/ai/jarno/nabu/block/JudeanDateBlock.java common/src/main/java/ai/jarno/nabu/registry common/src/main/resources
git commit -m "Add the Judean date palm, which stands tall once watered and fruits once boosted"
```

---

## Verification for Jarno

Claude Code cannot launch the game. Everything above is verified only to the extent that it compiles on both loaders. These need in-world checks, **on both Fabric and NeoForge**:

**Judean Date — the ladder**
1. Plant on a bone-dry planting bed → stays a single-block sprout indefinitely (age 1).
2. Water the bed → rises to two blocks, but never bears fruit.
3. Start a screw so the bed reads Boosted → reaches the fruiting stage and drops dates when broken.
4. Plant on ordinary moist vanilla farmland → reaches full height, never fruits. (Confirms `isMoist` reads farmland correctly.)

**Judean Date — edges**
5. Bone meal at each of the three tiers stops at exactly the same cap as natural growth.
6. Plant with a solid block two above it → stalls at age 1, does not destroy the ceiling.
7. Break the upper half → the lower half goes too, and exactly one set of loot drops. Repeat breaking the lower half.
8. Pick-block on either half yields Judean Date Seeds.
9. Eat a date → restores 2 hunger shanks.

**Emmer**
10. Right-click a fully grown Emmer → grain drops, plant remains at a partly grown stage, sound plays.
11. Right-click a partly grown Emmer → nothing happens, and holding a block still places it normally.
12. Leave a fully grown Emmer next to empty planting beds → it eventually sows one. (Expect minutes, not seconds.)
13. Put vanilla farmland in range instead → it must **never** sow there.

**Both**
14. Save and reload mid-growth → ages and the two-block structure survive.
15. Both crops appear in the Nabu creative tab; both seed types appear in Hanging Gardens chests.

**Known cosmetic gap:** all models use vanilla placeholder textures, matching what Silphium already does. Emmer Grain and Silphium share the `wheat` item texture and are distinguishable only by tooltip until your art lands. Textures needed: `emmer_stage0-7`, `judean_date_stage0/stage1`, `judean_date_stage{2,3,4}_{bottom,top}` under `textures/block/`, and `emmer`, `emmer_seeds`, `judean_date`, `judean_date_seeds` under `textures/item/`.
