"""Check the generated templates against the gameplay constants in `common`.

    python tools/verify_structures.py

The Wonder cannot be play-tested from here, so this stands in for it. It reassembles the
jigsaw stack -- every combination of pool variants, not just one -- and then asserts that

  * the seams pair up the way vanilla's JigsawBlock.canAttach demands, and the slabs are
    y-disjoint so no two pieces can ever be rejected for intersecting;
  * every lift column stands on real cistern water and is clear all the way up;
  * every water level below the deck is sealed in brick, so a delivered source cannot pour
    into the maintenance well a player is standing in;
  * every planting bed can actually reach BOOSTED, is claimed by exactly one data marker,
    and sits inside the shrine's reach;
  * the screws the shafts need are affordable from the chests that are actually placed.

A green run does not prove the Wonder looks good -- only that its wiring is sound.
"""
import itertools
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
from generate_structures import (
    BAYS, CHAMBERS, CHAMBER_CHESTS, CHAMBER_LOOT, CHEST_POSITIONS, CISTERN_Y, LINKS, NEWEL,
    OUT, REPO, SEAM_X, SEAM_Z, SHRINE_POS, SIZE_X, SIZE_Z, SLABS, deck_at, piece_names,
    stair_treads,
)

# Mirrored from common/. If these ever diverge, this check is worthless -- keep them in step.
BOOST_RADIUS, BOOST_HEIGHT = 4, 1          # PlantingBedBlock
WATER_RADIUS = 4                           # PlantingBedBlock.nearWater, dy in {0, +1}
MARKER_H, MARKER_V = 4, 2                  # BedMarkers
REACH_H, REACH_V = 32, 24                  # GardenControllerBlockEntity

CHEST_SCREWS_MIN = 5                       # loot_table/chests/hanging_gardens.json

AIR = "minecraft:air"
WATER = "minecraft:water"

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def load(name):
    data = nbt.read(os.path.join(OUT, name + ".nbt"))
    grid, tiles, states = {}, {}, {}
    for b in data["blocks"]:
        x, y, z = b["pos"]
        entry = data["palette"][b["state"]]
        grid[(x, y, z)] = entry["Name"]
        states[(x, y, z)] = entry
        if "nbt" in b:
            tiles[(x, y, z)] = b["nbt"]
    return data, grid, tiles, states


def solid(grid, pos):
    return grid.get(pos) not in (None, AIR, WATER)


def verify_seams(pieces):
    """The connector contract, checked the way JigsawBlock.canAttach checks it."""
    for parent, child in LINKS:
        (_, _, py1, _), _, ptiles, pstates = pieces[parent]
        (_, _, _, _), _, ctiles, cstates = pieces[child]
        up = (SEAM_X, py1, SEAM_Z)
        down = (SEAM_X, 0, SEAM_Z)
        check(pstates.get(up, {}).get("Name") == "minecraft:jigsaw",
              "%s: no jigsaw at its top seam %s" % (parent, up))
        check(cstates.get(down, {}).get("Name") == "minecraft:jigsaw",
              "%s: no jigsaw at its bottom seam %s" % (child, down))
        if up not in ptiles or down not in ctiles:
            continue
        pj, cj = ptiles[up], ctiles[down]
        po = pstates[up].get("Properties", {}).get("orientation")
        co = cstates[down].get("Properties", {}).get("orientation")
        check(po == "up_north", "%s top jigsaw orientation is %s, expected up_north" % (parent, po))
        check(co == "down_north", "%s base jigsaw orientation is %s, expected down_north" % (child, co))
        check(pj["target"] == cj["name"],
              "%s targets %s but %s is named %s" % (parent, pj["target"], child, cj["name"]))
        check(cj["target"] == pj["name"],
              "%s targets %s but %s is named %s" % (child, cj["target"], parent, pj["name"]))
        check(pj["pool"] == "nabu:hanging_gardens/%s" % child,
              "%s points at pool %s, expected the %s pool" % (parent, pj["pool"], child))
        check(pj["joint"] == "aligned" and cj["joint"] == "aligned",
              "%s/%s seam is not an aligned joint, so the tier could land rotated" % (parent, child))


# A withered shrub -- and therefore the garden fern it becomes -- needs a sturdy top face
# under it. Only full cubes qualify: walls, stairs, ladders and planting beds all fail
# isFaceSturdy(UP), and dead leaves fail it *after* reviving, because vanilla leaves report an
# empty block support shape. So a shrub resting on dead leaves survives right up until the
# sweep reaches the layer below it and then vanishes -- exactly the class of bug that made the
# greening look like it worked and then undid itself.
FULL_CUBES = {
    "nabu:babylonian_bricks", "nabu:cracked_babylonian_bricks", "nabu:mossy_babylonian_bricks",
    "nabu:polished_babylonian_bricks", "nabu:smooth_babylonian_bricks",
    "nabu:chiseled_babylonian_bricks", "nabu:glazed_babylonian_bricks",
    "nabu:babylonian_tiles", "nabu:mossy_babylonian_tiles",
    # Dead moss is a full cube and revives into one -- vanilla's moss block -- so unlike dead
    # leaves it still holds up whatever is standing on it after the sweep passes.
    "nabu:dead_moss",
}

FOLIAGE = {"nabu:dead_leaves", "nabu:withered_shrub", "nabu:withered_vine"}


def verify_foliage(tag, grid):
    """Decoration must survive being revived, and must never encroach on the puzzle."""
    # A lift column is off limits at every height, because it runs from the cistern to the
    # deck. Channels and planting only matter near their own deck -- the chambers sit directly
    # under some of them and are free to use that ground.
    columns = set()
    surface = {}
    for b in BAYS:
        for spot in (b["shaft"], b["wall"], b["well"]):
            if spot:
                columns.add(spot)
        for cell in list(b["channel"]) + list(b["beds"]):
            surface[cell] = b["deck"]

    shrubs = leaves = 0
    for (x, y, z), name in grid.items():
        if name not in FOLIAGE:
            continue
        check((x, z) not in columns,
              "%s: %s at (%d,%d,%d) sits in a lift column" % (tag, name, x, y, z))
        # Only the deck course and the one above it are the bay's: that is where planting and
        # channel live. A chamber tucked under a terrace is free to dress its own walls.
        deck = surface.get((x, z))
        check(deck is None or not (deck <= y <= deck + 1),
              "%s: %s at (%d,%d,%d) clutters bay ground at deck %s" % (tag, name, x, y, z, deck))
        if name == "nabu:withered_shrub":
            shrubs += 1
            below = grid.get((x, y - 1, z))
            check(below in FULL_CUBES,
                  "%s: shrub at (%d,%d,%d) stands on %s, which will not hold a fern" % (tag, x, y, z, below))
        elif name == "nabu:dead_leaves":
            leaves += 1
            # Nothing may rest on leaves: they lose their support shape the moment they revive.
            above = grid.get((x, y + 1, z))
            check(above not in ("nabu:withered_shrub",),
                  "%s: %s at (%d,%d,%d) rests on dead leaves and will drop when they revive"
                  % (tag, above, x, y + 1, z))

    check(shrubs > 100, "%s: only %d shrub(s) to revive into ferns" % (tag, shrubs))
    check(leaves > 200, "%s: only %d dead leaf block(s) to revive" % (tag, leaves))


# Blocks a player can stand inside. Everything else is either something to stand on or
# something in the way.
PASSABLE = {AIR, "nabu:withered_shrub", "nabu:garden_fern", "nabu:withered_vine",
            "minecraft:ladder", None}


def verify_earth(tag, grid):
    """Bare-earth drifts must be able to become, and stay, grass.

    `GrassBlock.isValidBonemealTarget` demands air directly above, so anything laid on a patch
    -- a parapet course, a cella wall, a dead shrub -- silently costs it its bloom. An opaque
    block above would also revert it to plain dirt on the next random tick.
    """
    columns = set()
    for b in BAYS:
        for spot in (b["shaft"], b["wall"], b["well"]):
            if spot:
                columns.add(spot)

    interior = set()
    for _, x0, x1, z0, z1, floor in CHAMBERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                interior.add((x, floor, z))

    patches = [p for p, n in grid.items() if n == "minecraft:coarse_dirt"]
    check(len(patches) > 40, "%s: only %d bare-earth cell(s) to green" % (tag, len(patches)))

    for (x, y, z) in patches:
        check(grid.get((x, y + 1, z)) == AIR,
              "%s: earth at (%d,%d,%d) has %s above it, so it can never bloom"
              % (tag, x, y, z, grid.get((x, y + 1, z))))
        check((x, z) not in columns,
              "%s: earth at (%d,%d,%d) sits in a lift column" % (tag, x, y, z))
        check((x, y, z) not in interior,
              "%s: earth at (%d,%d,%d) is a chamber floor -- grass belongs under the sky"
              % (tag, x, y, z))


def verify_moss(tag, grid):
    """Dried moss must sit on a floor -- deck or chamber -- and nowhere the puzzle needs.

    Far laxer than `verify_earth` on purpose: moss is a full cube that revives into another one,
    so it does not care what stands on it and nothing has to keep the sky clear above it. That
    is also why it is allowed indoors where the soil drifts are not. What it must not do is take
    a cell the lift or the planting owns, or end up buried in tier mass where nobody will ever
    see it revive.
    """
    columns = set()
    surface = {}
    for b in BAYS:
        for spot in (b["shaft"], b["wall"], b["well"]):
            if spot:
                columns.add(spot)
        for cell in list(b["channel"]) + list(b["beds"]):
            surface[cell] = b["deck"]

    floors = set()
    for _, x0, x1, z0, z1, floor in CHAMBERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                floors.add((x, floor, z))

    patches = [p for p, n in grid.items() if n == "nabu:dead_moss"]
    check(len(patches) > 40, "%s: only %d dead moss cell(s) to green" % (tag, len(patches)))
    check(any(p in floors for p in patches), "%s: no dead moss on any chamber floor" % tag)

    for (x, y, z) in patches:
        check((x, z) not in columns,
              "%s: dead moss at (%d,%d,%d) sits in a lift column" % (tag, x, y, z))
        check(surface.get((x, z)) != y,
              "%s: dead moss at (%d,%d,%d) has taken bay ground" % (tag, x, y, z))
        # It only ever replaces paving, so it is a surface underfoot rather than buried mass.
        check(deck_at(x, z) == y or (x, y, z) in floors,
              "%s: dead moss at (%d,%d,%d) is neither a deck nor a chamber floor (deck is %s)"
              % (tag, x, y, z, deck_at(x, z)))


def verify_interior(tag, grid):
    """The chambers must be reachable on foot from the cella, and only from the cella.

    This is the check the whole feature hangs on. A single mis-oriented tread or a clearance
    course left solid turns the interior into a sealed void that nothing in the templates
    would otherwise complain about.
    """
    def standable(p):
        x, y, z = p
        below = grid.get((x, y - 1, z))
        if below in PASSABLE or below == WATER:
            return False
        return grid.get(p) in PASSABLE and grid.get((x, y + 1, z)) in PASSABLE

    # Start on the cella floor just inside the doorway, which is where the ramp delivers you.
    start = (SEAM_X, 17, 16)
    check(standable(start), "%s: cannot stand on the cella floor at %s" % (tag, start))

    seen = {start}
    queue = [start]
    while queue:
        x, y, z = queue.pop()
        for nx, nz in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
            # Step up one, or drop up to three -- the spiral never asks for more than one.
            for ny in range(y + 1, y - 4, -1):
                nxt = (nx, ny, nz)
                if nxt in seen or not standable(nxt):
                    continue
                seen.add(nxt)
                queue.append(nxt)
                break

    for (cell, y) in stair_treads():
        stood = (cell[0], y + 1, cell[1])
        check(stood in seen, "%s: stair tread at %s is not reachable from the cella" % (tag, stood))

    # Every spot a player could stand in a chamber has to be walkable to from the entrance.
    # Testing corners alone is too weak: a single full-cube block grown in one doorway seals a
    # whole side room while the rest of the level still reads as fine.
    for name, x0, x1, z0, z1, floor in CHAMBERS:
        stranded = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(floor + 1, floor + 4):
                    if standable((x, y, z)) and (x, y, z) not in seen:
                        stranded.append((x, y, z))
        check(not stranded,
              "%s: %d spot(s) in %s cannot be walked to from the cella, e.g. %s"
              % (tag, len(stranded), name, stranded[:3]))

    for x, y, z, _ in CHAMBER_CHESTS:
        # The chest occupies its own cell, so check the floor beside it.
        beside = [(x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1)]
        check(any(p in seen for p in beside),
              "%s: chamber chest at (%d,%d,%d) cannot be reached" % (tag, x, y, z))

    # The newel is what the stair winds around; hollow it and the spiral has no core.
    nx, nz = NEWEL
    for y in range(4, 16):
        check(grid.get((nx, y, nz)) not in PASSABLE,
              "%s: stair newel is hollow at y=%d" % (tag, y))


def assemble(choice):
    """Stack one variant of each slab into a single world grid."""
    grid, tiles = {}, {}
    for (name, y0, y1, _), piece in zip(SLABS, choice):
        _, sub, subtiles, _ = load(piece)
        for (x, y, z), block in sub.items():
            grid[(x, y + y0, z)] = block
        for (x, y, z), tile in subtiles.items():
            tiles[(x, y + y0, z)] = tile
    return grid, tiles


def verify_assembly(choice, grid, tiles):
    tag = "+".join(choice)
    screws, waters = [], []

    for index, b in enumerate(BAYS):
        deck = b["deck"]
        sx, sz = b["shaft"]

        check(grid.get((sx, CISTERN_Y, sz)) == WATER,
              "%s: shaft %d intake at y=%d is %s, expected water"
              % (tag, index, CISTERN_Y, grid.get((sx, CISTERN_Y, sz))))
        for y in range(3, deck + 1):
            check(grid.get((sx, y, sz)) == AIR,
                  "%s: shaft %d is blocked at y=%d by %s" % (tag, index, y, grid.get((sx, y, sz))))

        for y in range(3, deck, 2):
            screws.append((sx, y, sz))
            waters.append((sx, y + 1, sz))

        # Every delivered source below the deck must be walled in on all four sides, or it
        # drains into the well and the chain stalls the moment a player clears the shaft.
        for wy in range(4, deck, 2):
            for nx, nz in ((sx - 1, sz), (sx + 1, sz), (sx, sz - 1), (sx, sz + 1)):
                check(solid(grid, (nx, wy, nz)),
                      "%s: shaft %d source at y=%d leaks toward (%d,%d)" % (tag, index, wy, nx, nz))

        if b["well"]:
            wx, wz = b["well"]
            dx, dz = b["wall"]
            check(solid(grid, (wx, CISTERN_Y, wz)),
                  "%s: well %d opens straight into the cistern" % (tag, index))
            for y in range(3, deck + 1):
                check(grid.get((wx, y, wz)) == "minecraft:ladder",
                      "%s: well %d has no ladder at y=%d" % (tag, index, y))
                check(solid(grid, (wx - 1, y, wz)),
                      "%s: well %d ladder at y=%d has nothing behind it" % (tag, index, y))
                expected_open = y % 2 == 1
                open_now = not solid(grid, (dx, y, dz))
                check(open_now == expected_open,
                      "%s: well %d divider at y=%d is %s, expected %s"
                      % (tag, index, y, "open" if open_now else "solid",
                         "open (screw level)" if expected_open else "solid (water level)"))

            # Surface flow must not find the well mouth or a hole to fall down.
            seen, queue = {(sx, deck, sz)}, [(sx, deck, sz)]
            while queue:
                cx, cy, cz = queue.pop()
                check(not (cx, cz) == (wx, wz),
                      "%s: bay %d channel drains into the maintenance well" % (tag, index))
                # The mouth is the one cell with the lift beneath it rather than a floor:
                # the top screw stands there and holds the source up.
                check((cx, cz) == (sx, sz) or solid(grid, (cx, cy - 1, cz)),
                      "%s: bay %d channel has no floor at (%d,%d)" % (tag, index, cx, cz))
                for nx, nz in ((cx - 1, cz), (cx + 1, cz), (cx, cz - 1), (cx, cz + 1)):
                    nxt = (nx, cy, nz)
                    if nxt not in seen and grid.get(nxt) == AIR:
                        seen.add(nxt)
                        queue.append(nxt)

    verify_foliage(tag, grid)
    verify_earth(tag, grid)
    verify_moss(tag, grid)
    verify_interior(tag, grid)

    beds = [p for p, n in grid.items() if n == "nabu:planting_bed"]
    check(bool(beds), "%s: no planting beds" % tag)

    markers = [p for p, t in tiles.items() if t.get("metadata") == "nabu:beds"]
    check(len(markers) == len(BAYS), "%s: %d bed markers, expected %d" % (tag, len(markers), len(BAYS)))

    shrines = [p for p, n in grid.items() if n == "nabu:garden_controller"]
    check(len(shrines) == 1, "%s: %d shrines, expected exactly 1" % (tag, len(shrines)))
    hx, hy, hz = shrines[0] if shrines else SHRINE_POS
    check(solid(grid, (hx, hy - 1, hz)), "%s: shrine has nothing solid under it" % tag)

    unboostable, unwatered, unclaimed, unreachable = [], [], [], []
    for (bx, by, bz) in beds:
        if not any(abs(bx - sx) <= BOOST_RADIUS and abs(by - sy) <= BOOST_HEIGHT and abs(bz - sz) <= BOOST_RADIUS
                   for sx, sy, sz in screws):
            unboostable.append((bx, by, bz))
        if not any(abs(bx - wx) <= WATER_RADIUS and wy in (by, by + 1) and abs(bz - wz) <= WATER_RADIUS
                   for wx, wy, wz in waters):
            unwatered.append((bx, by, bz))
        if not any(abs(bx - mx) <= MARKER_H and abs(by - my) <= MARKER_V and abs(bz - mz) <= MARKER_H
                   for mx, my, mz in markers):
            unclaimed.append((bx, by, bz))
        if not (abs(bx - hx) <= REACH_H and abs(by - hy) <= REACH_V and abs(bz - hz) <= REACH_H):
            unreachable.append((bx, by, bz))

    check(not unboostable, "%s: %d bed(s) with no screw in boost range, e.g. %s" % (tag, len(unboostable), unboostable[:3]))
    check(not unwatered, "%s: %d bed(s) with no delivered water in range, e.g. %s" % (tag, len(unwatered), unwatered[:3]))
    check(not unclaimed, "%s: %d bed(s) outside every data marker, e.g. %s" % (tag, len(unclaimed), unclaimed[:3]))
    check(not unreachable, "%s: %d bed(s) beyond the shrine's reach, e.g. %s" % (tag, len(unreachable), unreachable[:3]))

    # A marker must not reach across into a neighbouring terrace, or terrace indices blur.
    for mx, my, mz in markers:
        levels = {by for (bx, by, bz) in beds
                  if abs(bx - mx) <= MARKER_H and abs(by - my) <= MARKER_V and abs(bz - mz) <= MARKER_H}
        check(len(levels) == 1,
              "%s: marker at (%d,%d,%d) covers bed levels %s, expected exactly one"
              % (tag, mx, my, mz, sorted(levels)))

    terrace_levels = sorted({by for _, by, _ in beds})
    check(terrace_levels == sorted(b["deck"] for b in BAYS),
          "%s: bed levels %s do not match the terraces %s"
          % (tag, terrace_levels, [b["deck"] for b in BAYS]))

    chests = [(p, t) for p, t in tiles.items() if t.get("id") == "minecraft:chest"]
    check(len(chests) == len(CHEST_POSITIONS) + len(CHAMBER_CHESTS),
          "%s: %d chests, expected %d" % (tag, len(chests), len(CHEST_POSITIONS) + len(CHAMBER_CHESTS)))

    chamber_cells = {(x, y, z) for x, y, z, _ in CHAMBER_CHESTS}
    surface = 0
    for p, t in chests:
        # The interior deliberately draws on its own table. Putting the chamber chests on the
        # surface one would pour another 20-28 water screws into a puzzle budgeted at nine.
        want = CHAMBER_LOOT if p in chamber_cells else "nabu:chests/hanging_gardens"
        check(t.get("LootTable") == want,
              "%s: chest at %s has LootTable %s, expected %s" % (tag, p, t.get("LootTable"), want))
        if p not in chamber_cells:
            surface += 1

    budget = surface * CHEST_SCREWS_MIN
    check(len(screws) <= budget,
          "%s: the shafts need %d screws but the surface chests only guarantee %d"
          % (tag, len(screws), budget))

    return len(beds), len(screws)


def main():
    pieces = {}
    for name, y0, y1, variants in SLABS:
        for piece in piece_names(name, variants):
            data, grid, tiles, states = load(piece)
            check(data["size"] == [SIZE_X, y1 - y0 + 1, SIZE_Z],
                  "%s: size %s, expected %s" % (piece, data["size"], [SIZE_X, y1 - y0 + 1, SIZE_Z]))
            check(len(data["blocks"]) == SIZE_X * (y1 - y0 + 1) * SIZE_Z,
                  "%s: %d blocks, expected a full slab so terrain cannot intrude"
                  % (piece, len(data["blocks"])))
            pieces.setdefault(name, ((0, y0, y1 - y0, 0), grid, tiles, states))

    # Slabs tile the monument without overlapping, which is what makes the stack safe.
    spans = [(y0, y1) for _, y0, y1, _ in SLABS]
    for (a0, a1), (b0, b1) in zip(spans, spans[1:]):
        check(a1 + 1 == b0, "slabs %s and %s do not meet cleanly" % ((a0, a1), (b0, b1)))

    verify_seams(pieces)

    combos = list(itertools.product(*[piece_names(n, v) for n, _, _, v in SLABS]))
    bed_counts = []
    screws = 0
    for choice in combos:
        grid, tiles = assemble(choice)
        beds, screws = verify_assembly(choice, grid, tiles)
        bed_counts.append(beds)

    # Every id must be something that actually exists. The brick family is registered through
    # BrickSet rather than as literals, so a blockstate file counts as proof on its own.
    registry = ""
    reg_dir = os.path.join(REPO, "common", "src", "main", "java", "ai", "jarno", "nabu", "registry")
    for f in ("NabuBlocks.java", "NabuItems.java", "BrickSet.java"):
        registry += open(os.path.join(reg_dir, f), encoding="utf-8").read()
    blockstates = os.path.join(REPO, "common", "src", "main", "resources", "assets", "nabu", "blockstates")
    for name, _, _, variants in SLABS:
        for piece in piece_names(name, variants):
            data, _, _, _ = load(piece)
            for entry in data["palette"]:
                if not entry["Name"].startswith("nabu:"):
                    continue
                path = entry["Name"].split(":")[1]
                check('"%s"' % path in registry or os.path.exists(os.path.join(blockstates, path + ".json")),
                      "%s: palette references unregistered %s" % (piece, entry["Name"]))

    # The verifier's own constants must still match the code they stand in for.
    src = open(os.path.join(REPO, "common", "src", "main", "java", "ai", "jarno", "nabu",
                            "block", "PlantingBedBlock.java"), encoding="utf-8").read()
    check(re.search(r"BOOST_RADIUS\s*=\s*%d\b" % BOOST_RADIUS, src), "BOOST_RADIUS drifted from PlantingBedBlock")
    check(re.search(r"BOOST_HEIGHT\s*=\s*%d\b" % BOOST_HEIGHT, src), "BOOST_HEIGHT drifted from PlantingBedBlock")
    check(re.search(r"WATER_RADIUS\s*=\s*%d\b" % WATER_RADIUS, src), "WATER_RADIUS drifted from PlantingBedBlock")

    src = open(os.path.join(REPO, "common", "src", "main", "java", "ai", "jarno", "nabu",
                            "blockentity", "GardenControllerBlockEntity.java"), encoding="utf-8").read()
    check(re.search(r"REACH_HORIZONTAL\s*=\s*%d\b" % REACH_H, src),
          "REACH_HORIZONTAL drifted from GardenControllerBlockEntity")
    check(re.search(r"REACH_VERTICAL\s*=\s*%d\b" % REACH_V, src),
          "REACH_VERTICAL drifted from GardenControllerBlockEntity")

    src = open(os.path.join(REPO, "common", "src", "main", "java", "ai", "jarno", "nabu",
                            "worldgen", "BedMarkers.java"), encoding="utf-8").read()
    check(re.search(r"MARKER_RADIUS_HORIZONTAL\s*=\s*%d\b" % MARKER_H, src),
          "MARKER_RADIUS_HORIZONTAL drifted from BedMarkers")
    check(re.search(r"MARKER_RADIUS_VERTICAL\s*=\s*%d\b" % MARKER_V, src),
          "MARKER_RADIUS_VERTICAL drifted from BedMarkers")

    print("combinations checked: %d   screws needed: %d   beds: %d-%d"
          % (len(combos), screws, min(bed_counts), max(bed_counts)))

    if failures:
        print("\nFAILED:")
        for f in sorted(set(failures)):
            print("  -", f)
        return 1
    print("\nOK - the stack assembles, every source is sealed, every bed is boostable, "
          "watered, claimed by one marker and inside the shrine's reach.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
