# Babylonian Bricks — A Decorative Block Set

Date: 2026-07-28
Status: approved, ready for planning

## Goal

Add a full decorative stone set themed to the Gardens: four looks — **regular**, **cracked**,
**tiles**, **chiseled** — each with **stairs, slab and wall**. Sixteen blocks total.

This is a builder's set. It carries no gameplay logic, touches no controller state, and has
no interaction with beds, screws or crops. Its purpose is to give the Wonder a material
vocabulary of its own so the ruin reads as built rather than assembled from vanilla stone
bricks, and to let players extend the Gardens in the same material.

## Design invariants this must respect

From `CLAUDE.md`, the ones that actually bear on this work:

- **All gameplay logic lives in `common`.** Nothing here is loader-divergent — no platform
  hooks, no `@ExpectPlatform`. Both platform modules are untouched.
- **Data-driven by default.** Every blockstate, model, loot table, recipe, tag and advancement
  is hand-written JSON. Code is limited to registration and the one class Java forces on us.
- **Only Wonder beds count.** Nothing in this set registers to the controller or affects
  completion. It is inert with respect to the puzzle.
- **Terrace geometry is fixed.** This set does not alter worldgen. See *Out of scope*.

One invariant is **deliberately broken, with approval**: *art assets are Jarno's*. Placeholder
textures are generated here so the set is visible in-game immediately. See *Textures*.

## Verified API facts (MC 26.2)

Checked against `minecraft-merged-deobf-26.2.jar` via `javap` and by extracting the vanilla
asset JSON from the same jar. None of this is from memory.

- **`StairBlock`'s only constructor is `protected StairBlock(BlockState, Properties)`.**
  This is the single blocking fact in the whole design. NeoForge patches it public; Fabric
  does not; in `common` we get the vanilla signature either way. `new StairBlock(...)` will
  not compile. A subclass is required.
- `StairBlock.codec()` returns `MapCodec<? extends StairBlock>` — covariant, so a subclass is
  legal and may override it if ever needed. It does not need to.
- `SlabBlock(Properties)` and `WallBlock(Properties)` are both **public**. Used directly.
- Advancement directory is `data/<ns>/advancement/` — **singular**, as of 1.21.
- The `inventory_changed` item predicate takes `"items": [ { "items": "#tag" } ]` — a tag
  reference is accepted directly, so one criterion can match all sixteen blocks.
- Item models live at `assets/<ns>/items/<name>.json` wrapping
  `{ "model": { "type": "minecraft:model", "model": "..." } }` — the shape this repo already
  uses.
- Vanilla ships **both** block and item tags for `walls`, `stairs` and `slabs`. Both families
  need entries.
- Slab loot uses `minecraft:set_count` count `2.0` conditioned on
  `block_state_property` `type=double`, plus `minecraft:explosion_decay`.
- Stonecutting recipe shape is
  `{ "type": "minecraft:stonecutting", "ingredient": "<id>", "result": { "count": n, "id": "<id>" } }`.
- A JDK 17 exists at `~/.gradle/jdks/jdk-17.0.13+11`. System `java` is 1.8, which cannot run
  single-file source launch — texture generation must use the cached JDK 17.

## Naming

Vanilla convention: qualifier first, and derived forms use the **singular** "brick".

| Variant | Base block | Stairs | Slab | Wall |
| --- | --- | --- | --- | --- |
| Regular | `babylonian_bricks` | `babylonian_brick_stairs` | `babylonian_brick_slab` | `babylonian_brick_wall` |
| Cracked | `cracked_babylonian_bricks` | `cracked_babylonian_brick_stairs` | `cracked_babylonian_brick_slab` | `cracked_babylonian_brick_wall` |
| Tiles | `babylonian_tiles` | `babylonian_tile_stairs` | `babylonian_tile_slab` | `babylonian_tile_wall` |
| Chiseled | `chiseled_babylonian_bricks` | `chiseled_babylonian_brick_stairs` | `chiseled_babylonian_brick_slab` | `chiseled_babylonian_brick_wall` |

Chiseled getting stairs, a slab and a wall is a deliberate departure from vanilla, which
leaves chiseled variants standalone. Requested, and reasonable for a decorative set.

## Code

Three files touched, one added. Deliberately small — sixteen blocks should not mean sixteen
copies of the same six lines.

### New `ai.jarno.nabu.block.NabuStairBlock`

```java
public class NabuStairBlock extends StairBlock {
    public NabuStairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
        super(baseState, properties);
    }
}
```

That is the entire class. It exists solely because the superclass constructor is `protected`
(see *Verified API facts*). It adds no behaviour and overrides nothing — in particular it does
**not** override `codec()`, which correctly inherits `StairBlock.CODEC`.

### New `ai.jarno.nabu.registry.BrickSet`

A record bundling the four suppliers for one variant:

```java
public record BrickSet(RegistrySupplier<Block> block,
                       RegistrySupplier<NabuStairBlock> stairs,
                       RegistrySupplier<SlabBlock> slab,
                       RegistrySupplier<WallBlock> wall) { }
```

with an `all()` accessor returning the four in order, so `NabuItems` can iterate them.

### `NabuBlocks` — a `brickSet` helper plus four declarations

A private static `brickSet(String baseName, String formPrefix)` registers all four blocks and
returns a `BrickSet`. Two name parameters, not one, because the base is plural
(`babylonian_bricks`) while the forms are singular (`babylonian_brick_stairs`) — deriving one
from the other would be guesswork dressed up as cleverness.

Each block uses `BlockBehaviour.Properties.ofLegacyCopy(Blocks.STONE_BRICKS)` with its own
`.setId(Nabu.key(Registries.BLOCK, name))`, matching the existing convention in this file
exactly. Stairs take `<base>.get().defaultBlockState()`; the base is registered first within
the helper, so the supplier resolves.

Four public fields: `BABYLONIAN_BRICKS`, `CRACKED_BABYLONIAN_BRICKS`, `BABYLONIAN_TILES`,
`CHISELED_BABYLONIAN_BRICKS`.

### `NabuItems` — a `blockItems` helper

A private static `blockItems(BrickSet, String... names)` registering a `BlockItem` per form
through the existing `tabbed()` helper, so all sixteen land in the `nabu:main` creative tab
automatically. Registration order determines tab order: base, stairs, slab, wall, per variant,
variants in the order above.

## Textures

**This is the approved deviation from the `CLAUDE.md` art rule.** Four placeholder 16×16 PNGs
are generated so the set is testable in-game without waiting on art:

- `assets/nabu/textures/block/babylonian_bricks.png`
- `assets/nabu/textures/block/cracked_babylonian_bricks.png`
- `assets/nabu/textures/block/babylonian_tiles.png`
- `assets/nabu/textures/block/chiseled_babylonian_bricks.png`

Generated by a throwaway Java program run with the cached JDK 17, using `BufferedImage` and
`ImageIO`. Sandy mud-brick palette so the variants read as distinct at a glance — offset
courses for bricks, a fracture overlay for cracked, a flush grid for tiles, a centred motif
for chiseled.

**Only the four PNGs are committed.** The generator lives in the scratchpad and is not added
to the repo — this project has no datagen and hand-authored assets are its convention;
committing a one-shot art script would imply a pipeline that does not exist.

These are placeholders. Overwriting them with real art is a file drop with no JSON edits,
because every model already points at these exact paths.

## Assets

All formats copied from the vanilla 26.2 equivalents extracted from the jar, not recalled.

**Blockstates (16)** — one per block. Bases are single-variant. Slabs map
`type=bottom|top|double`, with `double` pointing at the base cube model. Stairs carry the full
40-variant `facing`×`half`×`shape` matrix. Walls are `multipart` with `up` plus four
`WallSide` directions.

**Block models (40):**

| Kind | Count | Detail |
| --- | --- | --- |
| Base cube | 4 | parent `minecraft:block/cube_all` |
| Stairs | 12 | `_stairs`, `_stairs_inner`, `_stairs_outer` per variant |
| Slab | 8 | `_slab`, `_slab_top` per variant; `double` reuses the base cube |
| Wall | 16 | `_wall_post`, `_wall_side`, `_wall_side_tall`, `_wall_inventory` per variant |

**Item models (16)** — under `assets/nabu/items/`. Walls point at their `_wall_inventory`
model; everything else points at its block model.

**Language** — sixteen `block.nabu.*` entries. No `item.nabu.*` entries: `BlockItem` inherits
the block's description id, so item keys for block items are dead weight. (The existing lang
file has some such duplicates; this does not propagate them.)

## Data

**Loot tables (16)** — each block drops itself. The twelve non-slab blocks use a
`survives_explosion` condition, matching the existing tables in this repo. The four slabs
instead follow vanilla's slab shape: a `set_count` of `2.0` conditioned on
`block_state_property` `type=double`, plus an `explosion_decay` function.

**Tags (10 files):**

- `data/nabu/tags/block/babylonian_bricks.json` — all sixteen blocks.
- `data/nabu/tags/item/babylonian_bricks.json` — all sixteen items. This is what the
  advancement matches on.
- `data/minecraft/tags/block/mineable/pickaxe.json` → `["#nabu:babylonian_bricks"]`
- `data/minecraft/tags/block/needs_stone_tool.json` → `["#nabu:babylonian_bricks"]`
- `data/minecraft/tags/{block,item}/walls.json` — the four walls. **Load-bearing:** without
  the block tag these walls will not connect to vanilla walls.
- `data/minecraft/tags/{block,item}/stairs.json` — the four stairs.
- `data/minecraft/tags/{block,item}/slabs.json` — the four slabs.

Routing the two mining tags through one mod-owned tag keeps the vanilla-namespace files to a
single line each, and gives players and datapacks a handle on the whole set.

## Obtaining

`babylonian_bricks` is the root of the set and has **no recipe of any kind**. It is obtained
only by mining it out of the Hanging Gardens ruin. Everything else derives from it.

### Recipes (34)

**Crafting grid (13)** — standard shapes: stairs 6→4, slab 3→6, wall 6→6, for each of the four
variants (12), plus `chiseled_babylonian_bricks` from two stacked `babylonian_brick_slab`,
mirroring vanilla's chiseled recipe.

**Smelting (1)** — `babylonian_bricks` → `cracked_babylonian_bricks`. Vanilla's convention for
cracked variants, and the only route to cracked.

**Stonecutting (20):**

| From | Produces |
| --- | --- |
| `babylonian_bricks` | its own stairs/slab/wall, `babylonian_tiles`, `chiseled_babylonian_bricks`, and all three forms of both tiles and chiseled (11) |
| `babylonian_tiles` | its own stairs/slab/wall (3) |
| `chiseled_babylonian_bricks` | its own stairs/slab/wall (3) |
| `cracked_babylonian_bricks` | its own stairs/slab/wall (3) |

Slabs yield count 2 from the stonecutter; everything else count 1. Cracked is reachable only
through the furnace, so the stonecutter cannot shortcut past the smelting step — cracked stays
a deliberate choice rather than a free alternate cut.

### Recipe unlocking — one advancement, not thirty-four

`data/nabu/advancement/recipes/babylonian_bricks.json`. A single `inventory_changed` criterion
matching the item tag `#nabu:babylonian_bricks`, rewarding **all 34 recipes** at once:

```json
{
  "parent": "minecraft:recipes/root",
  "criteria": {
    "has_babylonian_bricks": {
      "trigger": "minecraft:inventory_changed",
      "conditions": { "items": [ { "items": "#nabu:babylonian_bricks" } ] }
    }
  },
  "requirements": [ [ "has_babylonian_bricks" ] ],
  "rewards": { "recipes": [ "…all 34…" ] }
}
```

Picking up any one of the sixteen blocks lights up the entire set in the recipe book. Vanilla
would ship one advancement per recipe, each gated on its own ingredient; that would mean the
player discovers the set piecemeal and never learns the stonecutter is the efficient route.
Unlocking as a set means the moment the first brick comes off the ruin, the player can see
what the material can become.

No `display` block, so it never appears in the advancement screen — this is an unlock trigger,
not an achievement. That matches how vanilla's recipe advancements behave.

## File inventory

Exactly 133 new JSON files — 16 blockstates, 40 block models, 16 item models, 16 loot tables,
34 recipes, 1 advancement, 10 tags — plus 4 PNGs. Java: two new classes (`NabuStairBlock`,
`BrickSet`) and two modified (`NabuBlocks`, `NabuItems`). `en_us.json` is modified, not new.

Hand-authoring 133 JSON files
is a transcription-error machine, so the JSON is emitted from a templating script in the
scratchpad and the **output** is committed — same reasoning as the textures. The committed
result is ordinary hand-shaped JSON indistinguishable from the rest of the repo; the script
is a typing aid, not a build step, and is not added to the project.

## Testing

No test harness exists in this repo, and Claude Code cannot launch the game. Verification is:

1. `./gradlew build` — must compile on **both** Fabric and NeoForge. This is the one gate
   Claude Code can actually close.
2. A JSON well-formedness pass over every generated file before committing.
3. A cross-reference pass: every `model`, `ingredient`, `result.id`, loot `name` and tag entry
   resolves to a file or registered id that exists. Most failure modes here are typos in
   long repetitive names, and they surface in-game as missing-model purple rather than as
   build failures — so this check substitutes for the compiler.

In-game checks for Jarno, per loader:

- All sixteen blocks appear in the Nabu creative tab, in variant order, with correct names.
- Stairs form corners correctly; walls connect to each other **and** to vanilla walls; slabs
  stack into a double and drop two.
- Every block mines with a pickaxe and drops nothing to a wooden pickaxe or bare hand.
- Picking up one `babylonian_bricks` unlocks all 34 recipes in the recipe book at once.
- Stonecutter offers the full derived set from `babylonian_bricks`.
- Cracked is reachable only by smelting.
- All four textures render (placeholder art, but not missing-texture purple).

## Out of scope

Noted, not built:

- **Placing these blocks in the ruin.** The structure NBT is Jarno's hand-built asset and
  editing it is scope creep. Until `babylonian_bricks` appears in the structure, the set is
  creative-only in survival — the sixteen ids above are the handoff.
- **Real art.** Placeholders only, by explicit approval. Overwriting the four PNGs needs no
  JSON changes.
- ~~**Mossy variant.**~~ **Added 2026-07-28, after the original build.** Requested as a fifth
  variant, so `mossy_babylonian_bricks` plus its stairs, slab and wall now exist — 20 blocks
  and 42 recipes in total. It follows vanilla's own rule: craftable from the base bricks plus
  either a vine or a moss block, and **never stonecuttable from plain brick**, structurally the
  same as cracked requiring a furnace. Its three forms cut from mossy as usual.
  The moss-*growth* interaction floated here is still not built — mossy is a decorative
  variant, not a mechanic.
- **Polished and smooth. Added 2026-07-28, after mossy.** Requested as two further variants,
  bringing the set to **seven variants, 28 blocks, 60 recipes**. Polished joins the stonecutter
  fan-out from the base bricks and is also craftable as a 2×2, mirroring how vanilla polishes
  stone. Smooth is **smelted from polished, not from the bricks** — the bricks' furnace slot
  already belongs to cracked, and a second smelting recipe on the same ingredient would shadow
  it, since furnace recipes match on input alone. Firing the polished block keeps one recipe
  per input and makes smooth a two-step refinement. No stonecutting recipe reaches smooth.
- **Any gameplay behaviour** — no bed, screw, controller or crop interaction. This set is
  decorative and stays that way.
- **Waxed/weathering mechanics** or a copper-style progression between the four looks.
