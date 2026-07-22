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

**Bed tiers are a gradient, not a lock.** Dry / Watered / Boosted. Ordinary water (bucket, any vanilla source) gives **Watered** — normal farming, always available to everyone. Only a *running screw in range* gives **Boosted**. Boosted beats Watered. This is the anti-cheese: a player-placed water source can never produce Boosted, so the puzzle is the only path to the real reward, while nobody is locked out of ordinary farming.

**Extinct crops grow anywhere but only *fruit* when Boosted.** Growth and fruiting are separate checks.

**The screw uses charge-based hysteresis.** Intake has water → charge ramps → at threshold, activate. Intake loses water → charge decays → at zero, deactivate. Never place/remove on the same tick as the intake changes; that thrashes block updates.

**The screw's placed water source is *tracked*.** Store that we placed it (and where). On deactivate, only clear the block if it's still water. Clean up in the block entity's removal handler so breaking an active screw never strands a source. Never delete water we didn't place.

**Screw intake is satisfied by the reservoir OR another screw's output.** That's what enables the vertical chain.

**Completion latches per terrace and fires once.** A terrace is restored once ≥1 of its beds hits Boosted; latched terraces stay counted even if screws are later removed. Full completion fires **exactly once**, guarded by a `completed` flag.

**Only Wonder beds count.** Beds flagged at worldgen register themselves to the controller. Player-built screw gardens elsewhere must not affect completion state.

**One-time unlock ≠ live aura.** The dryad/charm unlock is permanent once earned. The area fertility aura reads *current* Boosted state and fades if the player tears out the screws. Two separate reads, don't merge them.

**Terrace geometry is fixed, never jigsaw-assembled.** Jigsaw vertical alignment is unreliable and the lift puzzle depends on predictable levels. Terrace skeleton, reservoir, shrine, controller, screw sockets, and broken channels are guaranteed geometry. Template pools are used **only** for interior rooms and variable bed pieces.

**Bed count is discovered, never assumed.** Bed pieces vary by pool weighting, so each piece self-registers its beds to the controller via a data marker at placement. No code should hardcode a bed count per terrace.

## Performance & multiplayer

- Prefer event-driven over per-tick scanning. The controller reacts to bed state transitions; it does not poll every bed every tick.
- Block-entity ticks stay cheap. No world scans in a tick loop.
- All state that matters must survive save/load — persist it on the block entity.
- Assume dedicated server. Keep rendering client-only; never touch renderer code from common logic paths.

## Things Claude Code cannot do here

Hand these back to me rather than faking them:

- **Launching the game / in-world testing.** I run both loaders and verify.
- **Building structure NBT.** M5 assets are hand-built in a creative world and exported by me. Don't generate placeholder structures and call the milestone done — tell me what pieces you need and what the connectors should be named.
- **Art assets.** Models, textures, sounds are mine unless I say otherwise.

## Definition of done (per milestone)

1. Compiles on both Fabric and NeoForge.
2. Logic sits in `common`; platform modules hold only hooks.
3. State persists across save/load where relevant.
4. Design invariants above are intact.
5. A short summary written for me: what changed, what deviated, what I need to test in-game.