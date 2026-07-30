# Presentation Pass — Advancements, Sound, the Tablet, and the Dead Garden

Date: 2026-07-29
Status: approved, ready for planning

## Goal

The mechanic works. Nothing tells the player it exists.

Four changes, all aimed at the same gap: a player who stumbles into the ruin should learn what
it is, be led through restoring it, and *hear* the thing come alive. A judge should be able to
open the advancement tab and read the feature list off it.

```
        discovery            instruction              feedback              payoff
   ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐
   │ dead vines and   │  │ the Clay Tablet │  │ screw loop       │  │ the vines green│
   │ shrubs, weathered│─>│ found in the    │─>│ bloom chime      │─>│ up as the aura │
   │ brick            │  │ chest, page 4   │  │ awakening swell  │  │ reaches them   │
   └──────────────────┘  │ is the how-to   │  └──────────────────┘  └────────────────┘
                         └─────────────────┘
                                  advancement chain runs the length of it
```

The four are not independent decoration. The tablet explains the puzzle the advancements track,
the sounds confirm each step the advancements award, and the withered plants are both the "this
ruin is dead" signal at the start and the "you fixed it" signal at the end.

## Design invariants this must respect

From `CLAUDE.md`:

- **All gameplay logic lives in `common`.** Two features need to reach client-only classes (the
  looping sound instance, the book screen). Both go behind a **no-op-defaulted interface in
  common** that `NabuClient` installs the real implementation into. No common class ever names
  a client type, so a dedicated server never loads one.
- **One-time unlock ≠ live aura.** The withered plants revive off the **live** aura, via
  `BonemealableBlock`. They are explicitly *not* latched to `completed`, and they wilt back to
  nothing only in the sense that they simply stop being converted — already-converted vines stay
  vines. Nothing here reads or writes `restored` or `completed`.
- **Wetting is immediate, drying is gradual.** The bloom chime hangs off the existing
  `→ BOOSTED` edge in `PlantingBedBlock.apply()`. It adds no new refresh path and drains nothing.
- **Data-driven by default.** The entire advancement chain, both loot changes, the processor
  list and every model is JSON. Code covers exactly four things that have no datapack
  representation: the sound registry, the criterion trigger, the tablet's screen, and the two
  blocks' revive behaviour.
- **Both loaders must stay in lockstep.** Nothing here is loader-divergent — no `@ExpectPlatform`,
  no platform events. Both platform modules are untouched apart from `NabuClient.init()` already
  being called from each client entrypoint.
- **No scope creep.** Particles on bloom, a dryad entity, terrace-progress chat messages, and a
  music track were all considered and cut. See *Out of scope*.

## Verified API facts (MC 26.2)

Checked against `minecraft-merged-deobf-26.2.jar` with `javap`, and against the asset index at
`fabric-loom/assets/indexes/26.2-32.json`. Not from memory — several of these differ from older
versions and two would have broken the build.

- **`CriterionTrigger` lives in `net.minecraft.advancements.triggers`**, not
  `net.minecraft.advancements`. The registry key is
  `Registries.TRIGGER_TYPE` → `ResourceKey<Registry<CriterionTrigger<?>>>`.
- `SimpleCriterionTrigger<T extends SimpleInstance>` is the base; it exposes
  `protected void trigger(ServerPlayer, Predicate<T>)`.
- `SimpleCriterionTrigger.SimpleInstance` requires `Optional<ContextAwarePredicate> player()`,
  and **`ContextAwarePredicate` is in `net.minecraft.advancements.predicates`**.
- `CriteriaTriggers.LOCATION` is a `PlayerTrigger` — this is `minecraft:location`.
- `LocationPredicate` carries `Optional<HolderSet<Structure>> structures()`, so a
  `"structures": "nabu:hanging_gardens"` condition is valid.
- `BookViewScreen(BookViewScreen.BookAccess)` is public, and
  **`BookAccess(List<Component>)` is a public record constructor**. The tablet needs no written
  book item and no data component — pages go straight in as `Component`s. The fallback plan
  discussed at design time is therefore unnecessary.
- `AbstractTickableSoundInstance(SoundEvent, SoundSource, RandomSource)` — takes a bare
  `SoundEvent`, not a `Holder`. Exposes `isStopped()` and a `protected final stop()`.
- `SoundEvent.createVariableRangeEvent(Identifier)` exists; `Registries.SOUND_EVENT` is the key.
- `Item.use(Level, Player, InteractionHand)` returns `InteractionResult`.
- `VineBlock(Properties)` is public and non-final. `UP/NORTH/EAST/SOUTH/WEST`,
  `PROPERTY_BY_DIRECTION` and `getPropertyForFace(Direction)` are public statics;
  `randomTick` is `protected`, so spreading can be suppressed by override.
- **There is no `DeadBushBlock` in 26.2.** The base for a small ground plant is the abstract
  `VegetationBlock(Properties)`, which supplies `mayPlaceOn`, `canSurvive`, `updateShape` and
  `isPathfindable`, and requires a `codec()`.
- `BonemealableBlock` has exactly three abstract methods — `isValidBonemealTarget(LevelReader,
  BlockPos, BlockState)`, `isBonemealSuccess(Level, RandomSource, BlockPos, BlockState)`,
  `performBonemeal(ServerLevel, RandomSource, BlockPos, BlockState)` — plus defaulted
  `getParticlePos` and `getType`.
- `Registries.PROCESSOR_LIST` exists, and the available `RuleTest`s are `AlwaysTrueTest`,
  `BlockMatchTest`, `BlockStateMatchTest`, `TagMatchTest`, `RandomBlockMatchTest`,
  `RandomBlockStateMatchTest`, plus the positional `LinearPosTest` / `AxisAlignedLinearPosTest`.

## 1. Sound

A `NabuSounds` `DeferredRegister<SoundEvent>` alongside the existing registries, plus
`assets/nabu/sounds.json`. Every entry points at a **vanilla `.ogg` path confirmed present in the
26.2 asset index**, so the mod ships audible today and Jarno can drop custom `.ogg` files in
later without a single Java change.

| Event | Vanilla source | Fires from |
| --- | --- | --- |
| `block.water_screw.running` | `liquid/water` | client loop while `RUNNING` |
| `block.water_screw.start` | `item/bucket/empty1` | `WaterScrewBlockEntity.activate()` |
| `block.water_screw.stop` | `item/bucket/fill1` | `WaterScrewBlockEntity.deactivate()` |
| `block.planting_bed.bloom` | `block/amethyst/resonate1‑4` | `PlantingBedBlock.apply()`, `→ BOOSTED` |
| `block.garden_shrine.terrace_restored` | `block/beacon/power1‑3` | `onTerraceRestored()` |
| `block.garden_shrine.awaken` | `block/conduit/activate` | `onCompleted()` |
| `block.garden_shrine.awaken_chime` | `block/amethyst/shimmer` | `onCompleted()`, layered |

Where a vanilla family has numbered variants they all go in the entry, so the bloom chime and
terrace confirm get natural variation for free. Every event gets a `subtitles.nabu.*` lang key.

### The running loop

`WaterScrewSoundInstance extends AbstractTickableSoundInstance`, client-only, positioned on the
screw. It stops itself when the block is no longer a running water screw — so removal, chunk
unload and deactivation are all one code path rather than three.

Starting it needs a client-side tick, and `WaterScrewBlock.getTicker` currently returns `null`
client-side by design. Rather than break that:

```
common:  interface ScrewAudio { void tick(Level, BlockPos, BlockState); }
         ScrewAudio.INSTANCE  — defaults to a no-op lambda
client:  NabuClient.init() installs the real one
```

`getTicker` returns an audio-only ticker on the client that calls `ScrewAudio.INSTANCE`. The
server ticker is untouched. On a dedicated server the field never leaves its no-op default and
`WaterScrewSoundInstance` is never classloaded.

This deliberately does **not** live in `WaterScrewRenderer`, even though a renderer is already
there: block-entity renderers are skipped for culled chunk sections, so a screw behind a wall
would drop its sound and pick it up again as you walk — audibly wrong.

### The bloom chime

One line at the existing `tier == BOOSTED && previous != BOOSTED` edge in
`PlantingBedBlock.apply()`, guarded on `Level` being a server level.

Pitch is randomised across roughly 0.9–1.1. This is not garnish: a single screw activating calls
`refreshBedsInRange`, which can flip several beds to Boosted **in the same tick**. At a fixed
pitch those stack into one flam-like smear; detuned, they chord.

### The awakening swell

`onCompleted()` currently plays a single `BEACON_ACTIVATE` and pops the charm. It becomes three
plays over ~2.5s, driven by a small countdown on `GardenControllerBlockEntity`:

| tick | event | pitch |
| --- | --- | --- |
| 0 | `awaken` | 1.0 |
| 20 | `awaken_chime` | 1.0 |
| 45 | `awaken_chime` | 1.5 |

That countdown is **deliberately not persisted**. `CLAUDE.md` requires state that matters to
survive save/load; a two-second audio flourish does not qualify, and persisting it would mean a
server restart mid-swell replays half a fanfare at a confused player. The `completed` latch it
hangs off *is* already persisted, and remains the thing that guarantees this fires once.

## 2. Advancement chain

Seven advancements under `data/nabu/advancement/hanging_gardens/`.

```
root  The Hanging Gardens          minecraft:location   { structures: nabu:hanging_gardens }
├── Words of Nabu                  inventory_changed    nabu:clay_tablet
└── Lift the Water                 placed_block         nabu:water_screw
    └── Living Soil                nabu:garden_progress { stage: boosted }
        ├── A Terrace Remembers    nabu:garden_progress { stage: terrace }    [goal]
        │   └── Awaken the Guardian nabu:garden_progress { stage: awakened }  [challenge]
        └── Something Long Lost    inventory_changed    silphium | judean_date
```

Background `nabu:textures/block/babylonian_bricks.png`; icons drawn from blocks and items that
already exist, except *Words of Nabu* which uses the new tablet.

### The custom trigger

`nabu:garden_progress`, one `SimpleCriterionTrigger` with a `StringRepresentable` stage enum
(`boosted` / `terrace` / `awakened`). Three rungs, one class.

This is needed because **none of those three moments has a player in scope**. A screw priming
boosts a bed whether or not anyone is watching; the controller latches a terrace from a block
entity tick. So the trigger fires at every `ServerPlayer` within range of the *event*:

| Stage | Fired from | Range |
| --- | --- | --- |
| `boosted` | `PlantingBedBlock.apply()` | 16 blocks of the bed |
| `terrace` | `GardenControllerBlockEntity.onTerraceRestored()` | controller reach |
| `awakened` | `GardenControllerBlockEntity.onCompleted()` | controller reach |

`apply()` takes a `Level`, so each site guards on `instanceof ServerLevel`. A shared
`NabuTriggers.fireNearby(ServerLevel, BlockPos, double, Stage)` helper keeps the three call
sites to one line each.

Radius attribution is accepted as good-enough on purpose: exact per-terrace player credit would
mean new persisted state on the block entity for a cosmetic toast.

### `Something Long Lost` must not accept emmer

All three crops gate fruiting on Boosted — `EmmerBlock extends ExtinctCropBlock`, so emmer is as
"lost" as the others in fiction. It is nonetheless **excluded from the criterion**, because
`chests/hanging_gardens` hands the player 2–4 `nabu:emmer` on opening. Including it would award
the rung from the loot chest, before the player has grown anything.

`nabu:silphium` and `nabu:judean_date` are harvest-only: neither appears in any **chest** loot
table, and their block loot tables only yield the harvest at the fruiting age, which
`ExtinctCropBlock` will not reach off a Boosted bed. So the rung means what it says. (The chest
contains `silphium_seeds` and `judean_date_seeds`, which are separate items.)

## 3. The Clay Tablet

`nabu:clay_tablet`, stacks to 1, `Rarity.UNCOMMON`, added to `chests/hanging_gardens` as its own
guaranteed pool so it cannot be rolled away.

Nabu is the Babylonian god of writing and scribes; the mod is named for him. A scribe's tablet
is the right object for this mod to explain itself with.

### Reading it

`ClayTabletItem.use` opens `BookViewScreen` over a `BookAccess` built from four translatable
components. Same holder pattern as the audio:

```
common:  interface TabletReader { void open(List<Component>); }
         TabletReader.INSTANCE — defaults to a no-op
client:  NabuClient.init() installs the real one
```

Pages live in **lang keys** (`item.nabu.clay_tablet.page1‑4`) rather than baked into a
`written_book_content` component on the loot entry. Three reasons: the text is translatable, every
tablet in the world is identical and can be corrected by a lang edit rather than a world reset,
and no loot-table JSON has to carry prose.

### The text

Page 1 — what it was:

> I, Nabu-zer-lishir, scribe of the tablet-house, set down what the terraces were. The king
> raised a mountain of brick for a queen who wept for the green hills of her country. Where
> Babylon had only flat land and dust, he built her hills; and on the hills, a forest.

Page 2 — how it worked:

> Water does not climb. So we taught it. Screws of cedar and bronze stood in their sockets from
> the basin upward, each turning the water one course higher, until it ran out along the topmost
> channel and fell terrace to terrace through the beds.

Page 3 — what went wrong:

> Then the river fell away, and the screws stood still. The beds drank what was left and went to
> dust. The vines dried on the brick. What we planted here grows nowhere else in the world now,
> and it sleeps in the soil, waiting for water that does not come.

Page 4 — the instructions, in character:

> If you are reading this, the tablet-house has outlasted me. Set the screws again in their
> sockets, beginning at the basin, and let each lift into the one above. The beds will know. And
> she who keeps the garden will wake, and reward the hand that woke her.

Page 4 is load-bearing. It states the reservoir-upward build order, that screws chain into one
another, and that the reward is real — the three things a player cannot otherwise discover
without reading source.

## 4. Withered decor

> **Superseded 2026-07-29, after implementation.** The aura-driven design below shipped and was
> then replaced at Jarno's direction: a third block (`nabu:dead_leaves`) was added, and the
> revive moved from `BonemealableBlock` to a one-time sweep gated on **every** registered bed
> being boosted at once. See *Amendment* at the end of this section. The original is kept because
> the reasoning about the aura invariant still holds and explains why the gate is a live read.

Two blocks, both `BonemealableBlock`:

| Block | Base | Revives to |
| --- | --- | --- |
| `nabu:withered_vine` | `VineBlock` | `Blocks.VINE`, face booleans preserved |
| `nabu:withered_shrub` | `VegetationBlock` | `Blocks.FERN` |

**No controller changes at all.** `GardenControllerBlockEntity.radiate()` already routes every
aura hit through `BonemealableBlock.isValidBonemealTarget` / `performBonemeal`, so implementing
the interface is the entire integration. Bone meal in hand works on them for the same reason.

Both override `isRandomlyTicking` to false — `VineBlock.randomTick` spreads, and dead things must
not. Withered vine keeps vine's placement, climbing and shear behaviour by inheritance.

Because the revive reads the live aura rather than the `completed` latch, the ruin greens up
progressively as terraces come online and stops advancing if the screws are torn out — which is
exactly the invariant separating the aura from the unlock.

### Amendment — the greening sweep

Three blocks now, none of them `BonemealableBlock`. They implement a `DeadFoliage` interface
instead, which answers one question: what does this become?

| Block | Base | Revives to |
| --- | --- | --- |
| `nabu:withered_vine` | `VineBlock` | `Blocks.VINE`, face booleans preserved |
| `nabu:withered_shrub` | `VegetationBlock` | `Blocks.FERN` |
| `nabu:dead_leaves` | plain `Block` | `Blocks.OAK_LEAVES` |

**Why it moved off the aura.** Routing through `BonemealableBlock` meant bone meal in hand
converted them, so a player could green the ruin without restoring anything. It also capped the
reach at the aura's 12 blocks, leaving vines on outer walls permanently dead.

**The gate is `liveBoostedBeds() == beds.size()`** — every registered bed boosted at once, which
is strictly harder than `completed` (that needs only one bed per terrace). It is a *live* read, so
the invariant above still governs: this is the garden genuinely running at full flow, not a record
that it once did. The resulting transformation is nonetheless **one-way**, latched on a persisted
`greened` flag, because the greening is a thing that happened to the world rather than a reading
of current state — a bed drying out later must not un-grow a tree.

**The sweep is sliced, one horizontal layer per tick** (49×49 over 33 layers, ~2s total).
A single-pass sweep of that volume would hitch the server, and the house rule is that
block-entity ticks stay cheap. The cursor is transient while `greened` is only set on the final
layer, so a restart mid-sweep just replays it — reviving an already-living block is a no-op,
which is what makes replay safe.

`dead_leaves` is a plain `Block`, **not** a `LeavesBlock` subclass: vanilla leaves carry
`distance`/`persistent` and decay with no log nearby, which would quietly delete set dressing from
a structure containing no trees. Its properties are built from `Properties.of()` rather than
copied off `OAK_LEAVES`, for the same `ofLegacyCopy` reason `JudeanDateBlock` documents.

All three drop themselves to **shears or Silk Touch**, and nothing otherwise.

### The rule processor

`data/nabu/worldgen/processor_list/hanging_gardens_weathering.json`, applied to the `skeleton`
and `terrace_beds` pool elements in place of `minecraft:empty`.

Rules use `RandomBlockMatchTest` on `nabu:babylonian_bricks`:

| Fraction | Becomes |
| --- | --- |
| ~20% | `nabu:cracked_babylonian_bricks` |
| ~12% | `nabu:mossy_babylonian_bricks` |

Pure data, and it means the hand-built NBT does not have to have decay painted into it by hand —
every generated Wonder weathers differently. Applied to the pools, so it is one edit to remove if
it reads wrong in-world.

## Files

**New — common Java:** `registry/NabuSounds`, `registry/NabuTriggers`,
`advancement/GardenProgressTrigger`, `item/ClayTabletItem`, `item/TabletReader`,
`blockentity/ScrewAudio`, `block/WitheredVineBlock`, `block/WitheredShrubBlock`.

**New — client Java:** `client/sound/WaterScrewSoundInstance`, `client/ClientScrewAudio`,
`client/ClientTabletReader`.

**Modified — common Java:** `Nabu` (register sounds + triggers), `NabuBlocks`, `NabuItems`,
`PlantingBedBlock`, `WaterScrewBlock`, `WaterScrewBlockEntity`, `GardenControllerBlockEntity`,
`client/NabuClient`.

**New — resources:** `assets/nabu/sounds.json`; blockstates, models and item wrappers for the two
blocks and the tablet; 7 advancement JSONs; 2 block loot tables; the processor list.

**Modified — resources:** `lang/en_us.json` (subtitles, advancement titles and descriptions,
tablet pages, three new block/item names), `chests/hanging_gardens.json`, both template pools,
`mineable`/`climbable`/`sword_efficient` tags as appropriate.

**Untouched:** both platform modules. Nothing here is loader-divergent.

## Testing

`./gradlew build` covers compilation on both loaders and is the only gate Claude Code can close.
Advancement and loot JSON get the same id cross-reference check the brick set used.

In-game, for Jarno. Most of this is single-loader-safe, but **the two holder interfaces are the
risk** — a mistake there shows up as either a missing sound or a hard crash on a dedicated server:

- The screw loop starts on activation, **continues while you stand behind a wall**, and stops on
  deactivation, on break, and on walking away. It does not double up if you leave and return.
- Several beds boosting in one tick chord rather than smear.
- The awakening is three layers, not one, and fires exactly once — restoring, breaking screws and
  re-restoring must not replay it.
- All seven advancements award, in order. Specifically: **opening the Wonder chest must not
  immediately grant "Something Long Lost"**.
- The tablet opens a readable book screen on both loaders, and all four pages render.
- **Join a dedicated server on each loader.** This is the one that catches a client class leaking
  into a common path.
- Withered vine and shrub convert under a live aura and under hand-applied bone meal; they do not
  spread on their own.
- A freshly generated Wonder shows cracked and mossy brick scattered through it.

## Out of scope

Considered and cut, recorded so they are not silently rediscovered:

- **Particles on bloom.** The chime is the feedback; the bed already changes texture.
- **A dryad entity.** "Awaken the guardian" stays the charm popping on the altar. An entity is a
  milestone, not a polish item.
- **Latching the greening to `completed`.** Would merge the live aura and the one-time unlock,
  which is a named invariant.
- **Per-player terrace attribution.** New persisted state for a cosmetic toast.
- **A music track for the Gardens.** Real composition, and not something to fake with a vanilla
  alias.
- **Advancements for the crop chains.** The recipe advancements already cover those; a second set
  would double up.
- **Retexturing the tablet's book screen.** Vanilla book background is fine and a custom one is
  art, not code.
