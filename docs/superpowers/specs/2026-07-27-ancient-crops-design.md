# Two More Ancient Crops — Judean Date and Emmer

Date: 2026-07-27
Status: approved, ready for planning

## Goal

Add two extinct crops alongside Silphium, each exercising a growth form Silphium does not:

- **Judean Date** — a two-block-tall crop.
- **Emmer** — a self-planting crop: it seeds itself onto neighbouring beds over time, and can
  be pushed onto one deliberately with bone meal. (Originally specced with a regrowing
  right-click harvest; that was replaced on 2026-07-28 — see below.)

Both are historically grounded the way Silphium is. The Judean date palm went extinct
around the 6th century and was germinated in 2005 from 2000-year-old seeds recovered at
Masada; emmer is the ancient wheat that fed Mesopotamia. Both fit "Echoes of the Past".

## Design invariants this must respect

From `CLAUDE.md`, load-bearing here:

- **Extinct crops grow anywhere but only *fruit* when Boosted.** Growth and fruiting are
  separate checks.
- **Bed tiers are a ladder.** Dry → Watered → Boosted, climbed in order.
- **Only Wonder beds count** for completion. Nothing here touches controller state.
- **No world scans in a tick loop.**
- **All gameplay logic in `common`.** Nothing in this design is loader-divergent, so both
  platform modules are untouched.

## Verified API facts (MC 26.2)

Checked against `minecraft-merged-deobf-26.2.jar` via `javap`, not from memory:

- `CropBlock.codec()` returns `MapCodec<? extends CropBlock>` — subclassable.
- `DoublePlantBlock.codec()` returns `MapCodec<? extends DoublePlantBlock>` — subclassable.
  `DoublePlantBlock extends VegetationBlock`.
- **`PitcherCropBlock.codec()` returns `MapCodec<PitcherCropBlock>` — invariant, therefore
  *not* subclassable.** Its growth helpers (`grow`, `canGrow`, `getLowerHalf`, `isDouble`,
  `canGrowInto`, `sufficientLight`) are all `private`, so there is nothing to reuse either.
  We model on it, we do not extend it.
- `SweetBerryBushBlock.useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)`
  returns `InteractionResult`, `protected` — this is the right-click-harvest hook.
- `BonemealableBlock` has exactly three abstract methods: `isValidBonemealTarget`,
  `isBonemealSuccess`, `performBonemeal`.
- The farmland block is `FarmlandBlock` in 26.2 (not `FarmBlock`), and its `MOISTURE` is
  `BlockStateProperties.MOISTURE` — the same property `PlantingBedBlock` already uses.
- `BlockTags.SUPPORTS_CROPS` exists; the repo's `data/minecraft/tags/block/supports_crops.json`
  merges `nabu:planting_bed` into it (tags merge by default).
- `Item.Properties.food(FoodProperties)` exists;
  `new FoodProperties.Builder().nutrition(int).saturationModifier(float).build()`.
- `InteractionResult` is an interface with `SUCCESS`, `SUCCESS_SERVER`, `CONSUME`, `FAIL`,
  `PASS` constants.
- `DoubleBlockHalf` has `LOWER`/`UPPER`, `getOtherHalf()`, `getDirectionToOther()`.

## Changes to existing code

Deliberately minimal. Two items only.

### `ExtinctCropBlock.codec()` return type

Currently declared `public MapCodec<ExtinctCropBlock> codec()`. Invariant generics mean no
subclass can override it — the same defect that blocks subclassing `PitcherCropBlock`.
Widen to `MapCodec<? extends ExtinctCropBlock>` so `EmmerBlock` can extend it. One line,
no behaviour change.

### New `ai.jarno.nabu.block.CropGates`

`ExtinctCropBlock` holds the boost check privately. Three crops now need it, and the
Judean Date is not a `CropBlock`, so it cannot inherit it. Extract a small final utility
class with two statics, both taking the position of the *crop*, not the soil:

- `isBoosted(LevelReader level, BlockPos cropPos)` — the block below is a `PlantingBedBlock`
  at `BedTier.BOOSTED`. Moved verbatim out of `ExtinctCropBlock`, which then delegates.
- `isMoist(LevelReader level, BlockPos cropPos)` — the block below has the `MOISTURE`
  property and its value is `> 0`. Works unchanged for a Watered or Boosted
  `PlantingBedBlock` *and* for ordinary `FarmlandBlock`, because both carry
  `BlockStateProperties.MOISTURE`. No null-tier special-casing needed.

`isMoist` deliberately reads moisture rather than `BedTier`, so ordinary farmland is
treated as a Watered bed for the height gate. That preserves "grows anywhere" while
letting the ladder show through on a bone-dry Wonder bed.

## Judean Date

### Block

`ai.jarno.nabu.block.JudeanDateBlock extends DoublePlantBlock implements BonemealableBlock`.

State:

- `AGE`, `IntegerProperty` 0–4 (`MAX_AGE = 4`).
- `HALF`, vanilla's `DoubleBlockHalf`.

Occupies one block at ages 0–1 and two blocks from age 2 (`DOUBLE_FROM = 2`). Both halves
carry the same `AGE` so the two models change together.

### The gate — the full three-rung ladder

| Soil beneath | Age cap | Result |
| --- | --- | --- |
| Dry bed (`isMoist` false) | 1 | Stunted single-block sprout |
| Watered/Boosted bed, or moist farmland (`isMoist` true, `isBoosted` false) | 3 | Full two-block palm, bare |
| Boosted bed (`isBoosted` true) | 4 | Two blocks, fruiting, drops dates |

Expressed as one `growthCap(level, lowerPos)` method returning `1`, `MAX_AGE - 1`, or
`MAX_AGE`. Every growth path — random tick and bone meal — funnels through it, so bone meal
cannot skip either gate. This mirrors `ExtinctCropBlock.growthCap` exactly.

### Growth

- Only the **lower** half is randomly ticking. `isRandomlyTicking` returns
  `state.getValue(HALF) == DoubleBlockHalf.LOWER`. The upper half is driven entirely by the
  lower one, so the two halves can never race and grow twice in a tick.
- Requires `getRawBrightness(pos, 0) >= 9`, matching `ExtinctCropBlock`.
- Growth chance: a fixed roll per random tick, as vanilla's pitcher crop does.
  `CropBlock.getGrowthSpeed` is `protected static` on `CropBlock`, so a `DoublePlantBlock`
  subclass legally cannot call it — this is why a fixed roll, not an oversight.
- The 1→2 transition additionally requires the block above to be replaceable/air. If it is
  not, growth stalls at 1 rather than destroying whatever is there. A palm under a ceiling
  waits.
- Growing writes both halves: lower at `age`, upper at `age` with `HALF = UPPER`.

### Placement, breaking, survival, drops

`DoublePlantBlock`'s inherited placement assumes a plant that is two blocks tall *from the
moment it is placed*. This crop is not — it starts as a single-block sprout. So, as
`PitcherCropBlock` also does:

- Override `getStateForPlacement` to return the lower half at age 0.
- Override `setPlacedBy` to **not** call `DoublePlantBlock.placeAt`, so no upper half is
  created on placement. Getting this wrong yields a floating orphan top block.
- Override `canSurvive`: the lower half needs valid soil beneath (`mayPlaceOn`), the upper
  half needs the lower half of this same block below it.

Then:

- `mayPlaceOn` checks `BlockTags.SUPPORTS_CROPS`, matching `CropBlock`, so it plants on
  planting beds and vanilla farmland.
- Upper-half survival and the break-one-break-both behaviour come from `DoublePlantBlock`
  (`canSurvive`, `updateShape`, `playerWillDestroy`/`preventDropFromBottomPart`).
- The loot table conditions every pool on `half=lower`, so the two halves cannot double-drop.
- `getCloneItemStack` returns `judean_date_seeds` for pick-block.
- **No trampling.** Silphium has none, and vanilla pitcher crop's self-destruct on being
  walked over would be a nasty surprise on a terrace the player walks around constantly.

### Items

- `judean_date_seeds` — `BlockItem` placing the crop.
- `judean_date` — food, `nutrition(4).saturationModifier(0.5F)`. Apple-tier hunger with
  notably better saturation: right for a dense dried fruit, worth the irrigation work, does
  not eclipse golden carrots. Tunable.

## Emmer

### Block

`ai.jarno.nabu.block.EmmerBlock extends ExtinctCropBlock`. Ages 0–7 from `CropBlock`. The
fruiting gate is inherited *unchanged* — capped at 6 unless the bed is Boosted. The only
override to the base behaviour is `getBaseSeedId()` returning `emmer_seeds`. Two behaviours
are added on top.

### ~~Right-click harvest~~ → Bone-meal spread

**Superseded 2026-07-28.** The original design had `useWithoutItem` pop 1–2 `emmer` at max age
and reset the plant to `REGROWN_AGE = 4`, leaving it standing. That was removed on request.
Emmer is now harvested by breaking it, like Silphium; the loot table already yields
`nabu:emmer` at age 7, so grain access is unchanged — what is lost is harvesting without
replanting.

In its place, **bone meal on a max-age plant sows a seedling on a nearby bed**:

- `isValidBonemealTarget` — true if the plant can still grow (inherited behaviour), *or* if it
  is at max age and at least one nearby bed can take a seedling.
- `performBonemeal` — grow if it can still grow, otherwise sow one seedling at a random valid
  target.
- With no valid target the block is not a bone-meal target at all, so nothing is consumed.

The second branch tests `isMaxAge`, **not** "growth is capped". Those look interchangeable and
are not: a plant on an unwatered bed is also capped, one stage short of fruiting, and hanging
the spread off that would hand the player the reward without ever running a screw. Max age is
Boosted-only by construction, so gating on it keeps the ladder intact — the same reasoning
that made the old right-click harvest safe.

### Self-seeding (the passive path)

The two spreading paths differ on purpose: this one runs on every mature plant, so it must
stay O(1); the bone-meal path above is triggered by a player and can afford to scan the whole
box, which is what makes it dependable rather than a coin flip. Both share one
`canSowAt` predicate, so "where may Emmer take root" is defined in exactly one place.

On random tick, when at `MAX_AGE`:

- Roll `SPREAD_CHANCE = 1 in 10`.
- On success, pick **one** random offset within radius 2 horizontally and ±1 vertically
  (a 5×5×3 box) — a single candidate, tested and discarded. Not a scan.
- Sow an age-0 Emmer there only if that position is air **and** the block directly below it
  is a `PlantingBedBlock`.

Consequences, stated deliberately:

- **Beds only, never vanilla farmland.** Emmer can never creep into a player's own farm.
- **O(1) per tick.** Testing one random candidate rather than scanning the box honours the
  no-world-scans rule. The cost is that spread is probabilistic — it will not reliably fill
  the nearest gap. Correct trade for something running on every mature plant.
- **Spreading does not consume the crop.** The Boosted-plus-bed gate and the finite number
  of beds are already the limiter; charging the player their harvest on top would make
  mature Emmer feel like it steals from them.
- Reaching age 7 already requires Boosted, so spread is gated on Boosted transitively —
  no separate check needed.

### Items

- `emmer_seeds` — `BlockItem` placing the crop.
- `emmer` — the grain. Inert, no food component. Reserved as a future crafting ingredient.

## Registration and data

Following the existing patterns in `NabuBlocks` / `NabuItems` exactly.

**`NabuBlocks`** — two entries, both `ofLegacyCopy(Blocks.WHEAT)` like Silphium, each with
its `setId(Nabu.key(Registries.BLOCK, …))`.

**`NabuItems`** — four entries, all via `tabbed()` so they land in the existing creative tab
automatically: `judean_date_seeds`, `judean_date` (with the food component), `emmer_seeds`,
`emmer`.

**Data files:**

- `data/nabu/loot_table/blocks/judean_date.json` — mirrors `silphium.json`, but every pool
  additionally conditioned on `half=lower`; fruit pool conditioned on `age=4`.
- `data/nabu/loot_table/blocks/emmer.json` — mirrors `silphium.json`, conditioned on `age=7`.
- `data/nabu/loot_table/chests/hanging_gardens.json` — add two pools for the new seeds so
  both crops are obtainable, matching the existing Silphium seed pool.
- No tag changes. `supports_crops` and `grows_crops` already list `nabu:planting_bed`, and
  both new crops read the same tag.

**Language** — `assets/nabu/lang/en_us.json`: `block.nabu.judean_date`, `block.nabu.emmer`,
and item entries for all four items.

## Assets

Silphium's models point at vanilla textures as placeholders (`minecraft:block/wheat_stage0`,
`minecraft:item/wheat_seeds`). The new crops follow the same convention, so the milestone is
playable and testable without art, and the art swap later is a texture-path edit only.

**Placeholders used** (all confirmed present in 26.2):

- Emmer: `minecraft:block/wheat_stage0`–`7`, parent `minecraft:block/crop`.
- Judean Date bottoms, ages 0–4 in order: `pitcher_crop_bottom_stage_1`, `_stage_1`,
  `_stage_2`, `_stage_3`, `_stage_4`. (Vanilla has no `_stage_0`; age 0 reuses stage 1.)
- Judean Date tops, ages 2–4 in order: `pitcher_crop_top`, `pitcher_crop_top_stage_3`,
  `pitcher_crop_top_stage_4`. (Vanilla has no top texture below stage 3; age 2 reuses the
  plain `pitcher_crop_top`.)
- Items: `minecraft:item/wheat_seeds`, `minecraft:item/wheat`, `minecraft:item/pitcher_pod`.

**Files created:**

- `blockstates/emmer.json` — 8 age variants.
- `models/block/emmer_stage0`–`7.json`.
- `blockstates/judean_date.json` — all 10 `age` × `half` combinations. Ages 0–1 with
  `half=upper` are never placed in-world but must still map to a model or the game logs a
  missing-model error; they point at the stage-0 model as a stub.
- `models/block/judean_date_stage0.json`, `_stage1.json`, and
  `_stage2/3/4_bottom.json` + `_stage2/3/4_top.json`.
- `items/*.json` + `models/item/*.json` for all four items.

**Needed from Jarno later** (art is his, per `CLAUDE.md`): real 16×16 textures at
`assets/nabu/textures/block/emmer_stage0-7.png`,
`assets/nabu/textures/block/judean_date_{stage0,stage1}.png`,
`judean_date_stage{2,3,4}_{bottom,top}.png`, and
`assets/nabu/textures/item/{emmer,emmer_seeds,judean_date,judean_date_seeds}.png`. Swapping
them in is a one-line edit per model file.

## Testing

Automated coverage is limited by there being no test harness in the repo today and no way
for Claude Code to launch the game. Verification is therefore:

1. `./gradlew build` — must compile on **both** Fabric and NeoForge.
2. In-game checks for Jarno, per loader:
   - Judean Date on a dry bed stays a single-block stub; wet the bed and it rises to two
     blocks but bears nothing; run a screw to Boosted and it fruits.
   - Bone meal on the Judean Date stops at the same caps as natural growth.
   - Judean Date under a ceiling stalls at age 1 and does not eat the ceiling block.
   - Breaking either half of a Judean Date drops exactly one set of loot.
   - Bone meal on a still-growing Emmer grows it, capped by the bed tier as before.
   - Bone meal on a **max-age** Emmer sows a seedling on a nearby bed and is consumed;
     with no bed in range nothing happens and the bone meal is **not** consumed.
   - Bone meal on an age-6 Emmer on an *unwatered* bed does nothing — it must not spread.
   - Emmer at full growth eventually seeds an empty planting bed nearby, and never seeds
     vanilla farmland placed next to it.
   - Both crops survive a save/load cycle mid-growth.

## Out of scope

Noted, not built:

- Uses for `nabu:silphium`, `nabu:emmer` — recipes for all three harvests should be designed
  together in a later milestone rather than piecemeal.
- Any interaction between the new crops and terrace completion. Completion still latches on
  bed tier alone; the controller is untouched.
- Composter entries and villager trades for the new items.
