# CLAUDE.md — Nabu

House rules for this repo. Read before every session. These persist across tasks; the per-task brief supplies the *what*, this file supplies the *how*.

## What this project is

**Nabu** — a Minecraft mod for the CurseForge "Echoes of the Past" ModJam 2026. It adds one explorable Wonder: the ruined **Hanging Gardens of Babylon**. The player restores its ancient irrigation by placing Archimedes water screws, lifting water up the terraces, which revives the dead planting beds and awakens a dryad guardian.

- **Mod id:** `nabu`
- **Package root:** `ai.jarno.nabu`
- **Minecraft:** 26.2 (Java Edition)
- **Loaders:** Fabric **and** NeoForge, from one codebase via Architectury

## Working agreement

- **Milestone discipline.** Work one milestone at a time. Finish it, stop, summarize what changed and any deviations from the brief, then wait for approval. Do not start the next milestone unsolicited.
- **Do it right once.** Prefer a correct, well-structured implementation over a fast one. If a shortcut will need rewriting in a later milestone, don't take it — say so instead.
- **Flag, don't guess.** If the brief conflicts with what the API actually supports on 26.2, stop and raise it rather than silently improvising a different design.
- **No scope creep.** Features not in the current milestone don't get "while I was in there" implementations. Note them for later.
- **Ask before large refactors.** Restructuring existing working code needs a check-in first.

## Version discipline

MC 26.2 is recent and API signatures differ from older tutorials.

- **Never assume version numbers or API shapes from memory.** Verify current NeoForge, Fabric Loader, Fabric API, Architectury API, and Architectury Loom versions against official sources before changing build files.
- MC 26.x is **de-obfuscated** (official Mojang mappings) — use official names throughout. Don't reach for Yarn/MCP/SRG names.
- If a tutorial-shaped pattern doesn't compile, assume the API moved, not that the approach is wrong. Check current docs.
- Verify signatures and constants against the jar, not memory: `javap -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar <fqcn>`. The `-sources.jar` beside it is a 22-byte stub; don't bother with it.
- For vanilla asset, model and loot JSON *formats*, read the real files out of `~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar` and mirror them exactly rather than recalling a schema.

## Architecture: the layering rule

**All gameplay logic lives in `common`.** Platform modules contain only what genuinely cannot be shared: loader event hooks, registration glue, entrypoints.

```
ai.jarno.nabu            common — all logic
  .block                 blocks
  .blockentity           block entities + renderers
  .item                  items
  .worldgen              structure/jigsaw wiring
  .registry              registration
ai.jarno.nabu.fabric     Fabric entrypoint + hooks only
ai.jarno.nabu.neoforge   NeoForge entrypoint + hooks only
```

- If you're writing game rules in a platform module, stop — it belongs in common with an abstraction behind it.
- Loader-divergent behaviour goes behind an interface in common, implemented per platform (Architectury `@ExpectPlatform` or a service). The known case is the **baby-spawn breeding hook**, which differs between loaders.
- **Both loaders must stay in lockstep.** A feature isn't done if it only works on one. Say explicitly when you've only verified one side.

## Data-driven by default

Structures are **NBT + JSON template pools**, not blocks placed from code. Recipes, loot, tags, worldgen config all live in `data/`. Reach for code only when data can't express it.

## Design invariants

These are load-bearing. Don't "simplify" them away — each one closes a specific exploit or bug.

**Bed tiers are a ladder, not a lock.** Dry / Watered / Boosted, climbed in order. Ordinary water (bucket, any vanilla source, rain) gives **Watered** — normal farming, always available to everyone. **Boosted needs both**: the bed must be wet *and* have a running screw in range. Water alone tops out at Watered however it got there, so a player-placed source can never produce Boosted — that's the anti-cheese, the puzzle is the only path to the real reward while nobody is locked out of ordinary farming. A screw alone doesn't skip a rung either: a bone-dry bed next to a running screw reads Dry until the water actually reaches it.

**Wetting is immediate, drying is gradual.** A bed re-reads its moisture whenever something pushes a refresh at it (a screw starting or stopping), so a delivered source boosts on the tick it lands. Decay stays on the random tick alone, so a bed fades out over time instead of snapping back to Dry the moment a screw stops. Never drain moisture from a refresh path.

**Extinct crops grow anywhere but only *fruit* when Boosted.** Growth and fruiting are separate checks.

**The screw uses charge-based hysteresis.** Intake has water → charge ramps → at threshold, activate. Intake loses water → charge decays → at zero, deactivate. Never place/remove on the same tick as the intake changes; that thrashes block updates.

**The screw's placed water source is *tracked*, and the claim is re-checked, not trusted.** Store that we placed it (and where). While the screw runs, re-evaluate the output every tick: air → place and claim; anything else → drop the claim, because whatever displaced our water isn't ours to remove. That's what makes a buried source come back once the obstruction is cleared, and what stops us deleting a player's own water dropped into the gap. On deactivate, only clear the block if it's still water. Clean up in the block entity's removal handler so breaking an active screw never strands a source. Never delete water we didn't place.

Output maintenance is **separate from** the charge hysteresis above — re-delivering doesn't touch the start/stop decision, so it can run every tick without thrashing.

**Screw intake is satisfied by the reservoir, another screw's output, OR a running screw directly below.** That's what enables the vertical chain, in both its forms: spaced (screw / water / screw, each lifting into the gap above it) and stacked flush (screw on screw, where there's no gap for a source block at all). Only the topmost screw in a flush stack delivers water — the ones below it run, own nothing, and exist to pass the intake signal up.

**A stack primes one stage at a time.** Each screw earns its charge from empty, so water surfaces at the top of a column only after every screw beneath it has come up — roughly `CHARGE_MAX` ticks per stage. That staged delay is the visible feedback that the lift is working; don't "optimise" it by propagating activation instantly. Shutdown cascades the same way in reverse.

**Completion latches per terrace and fires once.** A terrace is restored once ≥1 of its beds hits Boosted; latched terraces stay counted even if screws are later removed. Full completion fires **exactly once**, guarded by a `completed` flag.

**Only Wonder beds count.** Beds flagged at worldgen register themselves to the controller. Player-built screw gardens elsewhere must not affect completion state.

**One-time unlock ≠ live aura.** The dryad/charm unlock is permanent once earned. The area fertility aura reads *current* Boosted state and fades if the player tears out the screws. Two separate reads, don't merge them.

**The ziggurat is jigsaw-assembled from full-footprint slabs.** Plinth, three terraces and the summit are separate pool pieces, stacked by one `aligned` up/down jigsaw pair each at dead centre. Every piece is a full 33×4×33 slab over the *same* footprint, so their boxes are **y-disjoint** — that is the entire safety argument, and it is why a piece must never be trimmed down to just its own tier. Inside those pieces the decks, reservoir, shrine, shafts, wells, dividers and channels are guaranteed geometry; pool *variants* differ only in bed layout and decoration.

**A jigsaw child can never overlap a placed piece.** Vanilla rejects any candidate whose bounding box intersects one already placed, so nothing can be socketed *inside* a terrace slab. That is why the interior chambers are carved into the tier pieces rather than attached as their own. Real room pieces would mean splitting every tier into disjoint rectangles (four bands plus a core), multiplying both the piece count and the failure surface. This fails silently at worldgen, not at build — don't rediscover it.

**Bed decks sit on even levels.** From the y=2 cistern, screws land on odd courses and their delivered sources on even ones (3→4, 5→6, 7→8), so an odd deck would put the water one course off the planting. Moving a terrace means moving it by two.

**A revived block must survive its own revival.** `DeadFoliage.revived` is handed straight to `setBlock`, so the living state meets a neighbour update on the same tick. Vanilla leaves decay with no log in range — set `PERSISTENT`. Vanilla `fern` keeps `VegetationBlock`'s soil-only `mayPlaceOn` and is culled off brick within a tick, which is the whole reason `GardenFernBlock` exists. Nothing may rest *on* dead leaves either: revived leaves report an empty block support shape. Always check what the **target** state requires, not what the dead one tolerated.

**The surface chests fund the lift; nothing else may.** The three shafts' screw cost is budgeted against the guaranteed minimum from the two reservoir-court chests. Interior chambers draw on their own table with no screws in it. Screws from any other source dissolve the constraint the puzzle is built on.

**The shrine is off-centre, so its ranges are measured from an edge.** It stands in the reservoir court on the north face, not at the middle. Bed-adoption reach and the greening radius therefore have to span the full footprint from a corner origin — growing the monument means re-checking both, or the far side silently stops being greened.

**Bed count is discovered, never assumed.** Bed pieces vary by pool weighting, so each piece self-registers its beds to the controller via a data marker at placement. No code should hardcode a bed count per terrace.

## Performance & multiplayer

- Prefer event-driven over per-tick scanning. The controller reacts to bed state transitions; it does not poll every bed every tick.
- Block-entity ticks stay cheap. No world scans in a tick loop.
- All state that matters must survive save/load — persist it on the block entity.
- Assume dedicated server. Keep rendering client-only; never touch renderer code from common logic paths.

## Things Claude Code cannot do here

Hand these back to me rather than faking them:

- **Launching the game / in-world testing.** I run both loaders and verify.
- **Judging whether it generates or looks right.** The Wonder's `.nbt` are *generated output* from `tools/generate_structures.py` — never hand-edit them, change the generator and re-run. `tools/verify_structures.py` reassembles every pool-variant combination and checks seams, water seals, bed boostability, foliage survivability and interior reachability, but a green run only ever proves the wiring. Whether the stack actually assembles in-world, and whether it reads well, is mine to confirm.
- **Art assets.** Models, textures, sounds are mine unless I say otherwise.

## Definition of done (per milestone)

1. Compiles on both Fabric and NeoForge.
   `:fabric:classes` / `:neoforge:classes` do **not** re-run `:common:processResources`, so after touching anything under `data/` or `assets/` the build output is stale and the game would load the old files. Run `./gradlew :common:processResources` (or a full `build`) before believing it.
2. Logic sits in `common`; platform modules hold only hooks.
3. State persists across save/load where relevant.
4. Design invariants above are intact.
5. A short summary written for me: what changed, what deviated, what I need to test in-game.