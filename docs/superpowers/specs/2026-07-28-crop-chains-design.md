# Crop Processing Chains — Bread, Resin, Syrup, Cake, Remedy

Date: 2026-07-28
Status: approved, ready for planning

## Goal

Give the three crops something to become. Each gets a short chain ending in a food or an
effect, and the chains cross once so the set reads as designed rather than three parallel
silos.

```
emmer ──────────3───────────────> emmer_bread
emmer ──────────3───────────────┐
                                 ├────────────> date_cake   <- the crossing point
judean_date ─3 + glass bottle──> date_syrup ────┘

silphium ───────2───────────────> silphium_resin ─┬─ + #nabu:spiceable ─> spiced_meat
                                                  └─ brewing ──────────> Ancient Remedy
```

Note that bread and cake are **siblings**, both drawn straight from grain. Bread is not an
input to the cake.

Historically grounded, as the crops are. Emmer fed Mesopotamia as bread and groats. Silphium's
resin — *laserpicium* — was the actual traded product, prized as both the Roman world's premier
seasoning and a cure-all. Date syrup (*dibs*) was Mesopotamia's primary sweetener long before
honey was common, which makes the cake specifically Babylonian rather than generically ancient.

## Design invariants this must respect

From `CLAUDE.md`:

- **All gameplay logic lives in `common`.** The brewing mixes are declared once in common; the
  platform modules hold only the hook that feeds them in. This mirrors the fertility charm,
  where all policy sits in `FertilityCharm` and each loader supplies only the trigger.
- **Both loaders must stay in lockstep.** The potion is the first loader-divergent feature
  since the breeding hook, and is not done until it brews on both.
- **Data-driven by default.** Every recipe and tag is JSON. Code covers registration and the
  brewing hook only, because brewing has no datapack representation (see below).
- **No scope creep.** Milling, porridge, bulgur and date wine were all considered and cut.

## Verified API facts (MC 26.2)

Checked against `minecraft-merged-deobf-26.2.jar` with `javap`, not from memory:

- **`PotionBrewing` mixes are code-only.** Mixes are assembled by
  `PotionBrewing.bootstrap(FeatureFlagSet)` through a `Builder`; there is no datapack recipe
  type for brewing. (`data/minecraft/recipe/brewing_stand.json` is the crafting recipe for the
  *block*, not a mix.) This is the whole reason the potion needs platform code.
- **Both loaders expose a hook.** Fabric:
  `net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder` with a `BuildCallback`, in
  `fabric-content-registries-v0` (present in Fabric API 0.155.2+26.2). NeoForge:
  `RegisterBrewingRecipesEvent`.
- `Potion(String name, MobEffectInstance...)` is public; `MobEffects.RESISTANCE` is a
  `Holder<MobEffect>`.
- `Item.Properties.food(FoodProperties)` and `food(FoodProperties, Consumable)` both exist.
- `Item.Properties.usingConvertsTo(Item)` — what an item becomes when consumed.
- `Item.Properties.craftRemainder(Item)` — what it leaves behind when used in crafting.
- `Consumables.DEFAULT_DRINK` and `Consumables.HONEY_BOTTLE` exist as ready-made `Consumable`s.
- `Items.GLASS_BOTTLE` exists.
- **There is no vanilla "cooked meat" item tag.** `#minecraft:meat` exists but contains raw
  beef, chicken and porkchop alongside the cooked ones, so it cannot gate a *cooking* recipe.
  This is why the set defines its own tag.

## Items

Five new items plus two potions.

| Item | Nutrition | Saturation | Notes |
| --- | --- | --- | --- |
| `date_syrup` | 2 | 0.4 | Bottled. Mainly an ingredient; the sip is a bonus. |
| `emmer_bread` | 6 | 0.8 | Vanilla bread is 5 / 0.6 — better, without eclipsing it. |
| `spiced_meat` | 7 | 1.1 | Saturation-led, because seasoning is the point. |
| `date_cake` | 8 | 1.0 | Steak-tier. The payoff for running two chains. |
| `silphium_resin` | — | — | Not food. The refined intermediate both silphium uses need. |

### `date_syrup` is bottled

Three properties, all verified above:

- `.food(FoodProperties(2, 0.4F), Consumables.DEFAULT_DRINK)` — drunk, not eaten.
- `.usingConvertsTo(Items.GLASS_BOTTLE)` — drinking returns the empty bottle.
- `.craftRemainder(Items.GLASS_BOTTLE)` — **crafting** with it returns the bottle too, so
  making a cake does not silently eat glass. Honey bottle behaves exactly this way.
- `.stacksTo(16)`, matching every other bottled item.

## Recipes

All crafting-grid, all JSON.

| Result | Shape | Ingredients |
| --- | --- | --- |
| `emmer_bread` ×1 | shaped, one row `###` | 3 × `nabu:emmer`, mirroring vanilla bread |
| `silphium_resin` ×1 | shapeless | 2 × `nabu:silphium` |
| `date_syrup` ×1 | shapeless | 3 × `nabu:judean_date` + 1 × `minecraft:glass_bottle` |
| `date_cake` ×1 | shapeless | 1 × `nabu:date_syrup` + 3 × `nabu:emmer` |
| `spiced_meat` ×1 | shapeless | 1 × `#nabu:spiceable` + 1 × `nabu:silphium_resin` |

**The cake takes raw emmer, not bread.** Grain and syrup meet directly; bread is a parallel
sibling, not a prerequisite. This keeps both emmer foods reachable from the same harvest rather
than making one gate the other.

### The `nabu:spiceable` tag

`cooked_beef`, `cooked_porkchop`, `cooked_chicken`, `cooked_mutton`, `cooked_rabbit`,
`cooked_cod`, `cooked_salmon`. Its own tag rather than `#minecraft:meat`, which would let you
season a raw chicken.

## The Ancient Remedy

| Potion | Effect | Duration |
| --- | --- | --- |
| `nabu:ancient_remedy` | Resistance I | 3:00 |
| `nabu:long_ancient_remedy` | Resistance I | 8:00 |

Brewing: awkward potion + `silphium_resin` → Ancient Remedy; + redstone → the long variant.

**No Resistance II variant, deliberately.** Unlike Regeneration, vanilla offers *no* brewing
path to Resistance at all, so there is no existing cost to anchor against — this is new
capability rather than a farmable alternative to something players already have. Resistance I
is 20% damage reduction; Resistance II is 40%, and an infinitely farmable source of that would
be hard to walk back. The long variant covers the same ground more safely.

### Architecture

- `NabuPotions` — a `DeferredRegister<Potion>`, following `NabuBlocks`/`NabuItems` exactly.
- `ai.jarno.nabu.brewing.NabuBrewing` in **common** — declares the mixes as plain data
  (base potion, ingredient item, result potion) and exposes them for a platform to apply. All
  balance decisions live here, so the two loaders cannot drift apart.
- `fabric` — registers a `FabricPotionBrewingBuilder.BuildCallback` that applies them.
- `neoforge` — subscribes `RegisterBrewingRecipesEvent` and applies them.

Neither platform file makes a decision; each only knows how to hand common's list to its own
loader. Same division as the fertility charm.

## Assets

Five item textures under `assets/nabu/textures/item/`, plus models and `items/` wrappers, plus
lang entries for the five items and both potions.

Generated placeholders in the established palette, as with the crops: resin as an amber lump,
syrup as a filled bottle, bread and cake as baked goods, spiced meat as a cut with flecks on
it. Replacing any of them stays a file drop with no JSON change.

## Testing

`./gradlew build` covers compilation on both loaders and is the only gate Claude Code can
close. The JSON cross-reference check covers recipe and tag ids.

In-game, for Jarno, **per loader** — the brewing hook is the one thing that can work on one
loader and silently not the other:

- All five items craft, and appear in the Nabu tab.
- Drinking date syrup returns a glass bottle; crafting a cake with it also returns the bottle.
- Spicing works on every one of the seven cooked meats, and on none of the raw ones.
- Awkward potion + silphium resin yields Ancient Remedy **on Fabric and on NeoForge**.
- Redstone extends it to 8:00; glowstone does **not** produce a Resistance II variant.
- Resistance actually applies on drinking, at the stated durations.

## Out of scope

Considered and cut, recorded so they are not silently rediscovered:

- **Milling.** No quern, no flour. Emmer goes straight to bread.
- **Porridge, groats, bulgur.** Historically the commoner preparation, but one bread is enough.
- **Date wine.** Accurate, but fermentation and alcohol are not wanted here. Not replaced with
  a "nectar" either — that would be an item with no reason to exist once syrup is in.
- **Per-meat spiced variants.** Seven near-identical foods for one idea.
- **A Resistance II remedy.** See above.
