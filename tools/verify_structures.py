"""Check the generated templates against the gameplay constants in `common`.

    python tools/verify_structures.py

The structure cannot be play-tested from here, so this stands in for it: it simulates the screws
being placed and the water they would deliver, then asserts that every planting bed can actually
reach BOOSTED, that every bed is claimed by a data marker, and that the shrine can see them all.
A green run does not prove the Wonder looks good -- only that its wiring is sound.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt
from generate_structures import OUT, REPO, TERRACES, SHRINE_POS

# Mirrored from common/. If these ever diverge, this check is worthless -- keep them in step.
BOOST_RADIUS, BOOST_HEIGHT = 4, 1          # PlantingBedBlock
WATER_RADIUS = 4                           # PlantingBedBlock.nearWater, dy in {0, +1}
MARKER_H, MARKER_V = 4, 2                  # BedMarkers
REACH_H, REACH_V = 16, 12                  # GardenControllerBlockEntity

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def load(name):
    data = nbt.read(os.path.join(OUT, name + ".nbt"))
    grid = {}
    tiles = {}
    for b in data["blocks"]:
        x, y, z = b["pos"]
        grid[(x, y, z)] = data["palette"][b["state"]]["Name"]
        if "nbt" in b:
            tiles[(x, y, z)] = b["nbt"]
    return data, grid, tiles


def main():
    data, grid, tiles = load("skeleton")
    print(f"skeleton {data['size']}  blocks={len(data['blocks'])}  palette={len(data['palette'])}")

    screws, waters = [], []
    for sx, sz, bed_y in TERRACES:
        for y in range(2, bed_y, 2):
            screws.append((sx, y, sz))
            waters.append((sx, y + 1, sz))

    # The column a player has to be able to fill, and the source the bottom screw draws on.
    for sx, sz, bed_y in TERRACES:
        check(grid.get((sx, 1, sz)) == "minecraft:water",
              f"shaft ({sx},{sz}): intake at y=1 is {grid.get((sx, 1, sz))}, expected water")
        for y in range(2, bed_y + 1):
            check(grid.get((sx, y, sz)) == "minecraft:air",
                  f"shaft ({sx},{sz}): y={y} is {grid.get((sx, y, sz))}, expected air")

    beds = [p for p, n in grid.items() if n == "nabu:planting_bed"]
    check(bool(beds), "no planting beds in the template")

    markers = [p for p, t in tiles.items() if t.get("metadata") == "nabu:beds"]
    check(len(markers) == len(TERRACES), f"{len(markers)} bed markers, expected {len(TERRACES)}")

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
        hx, hy, hz = SHRINE_POS
        if not (abs(bx - hx) <= REACH_H and abs(by - hy) <= REACH_V and abs(bz - hz) <= REACH_H):
            unreachable.append((bx, by, bz))

    check(not unboostable, f"{len(unboostable)} bed(s) have no screw in boost range, e.g. {unboostable[:3]}")
    check(not unwatered, f"{len(unwatered)} bed(s) have no delivered water in range, e.g. {unwatered[:3]}")
    check(not unclaimed, f"{len(unclaimed)} bed(s) outside every data marker, e.g. {unclaimed[:3]}")
    check(not unreachable, f"{len(unreachable)} bed(s) beyond the shrine's reach, e.g. {unreachable[:3]}")

    # A marker must not reach across into a neighbouring terrace, or terrace indices blur.
    for mx, my, mz in markers:
        levels = {by for (bx, by, bz) in beds
                  if abs(bx - mx) <= MARKER_H and abs(by - my) <= MARKER_V and abs(bz - mz) <= MARKER_H}
        check(len(levels) == 1, f"marker at ({mx},{my},{mz}) covers bed levels {sorted(levels)}, expected exactly one")

    terrace_levels = sorted({by for _, by, _ in beds})
    check(terrace_levels == sorted(t[2] for t in TERRACES),
          f"bed levels {terrace_levels} do not match the terraces {[t[2] for t in TERRACES]}")

    chests = [(p, t) for p, t in tiles.items() if t.get("id") == "minecraft:chest"]
    check(bool(chests), "no chest in the template")
    for p, t in chests:
        check(t.get("LootTable") == "nabu:chests/hanging_gardens", f"chest at {p} has LootTable {t.get('LootTable')}")

    shrines = [p for p, n in grid.items() if n == "nabu:garden_controller"]
    check(len(shrines) == 1, f"{len(shrines)} shrines, expected exactly 1")
    if shrines:
        sx, sy, sz = shrines[0]
        check(grid.get((sx, sy - 1, sz)) not in (None, "minecraft:air", "minecraft:water"),
              "shrine has nothing solid under it")

    # Every id must be something that actually exists.
    registry = ""
    reg_dir = os.path.join(REPO, "common", "src", "main", "java", "ai", "jarno", "nabu", "registry")
    for f in ("NabuBlocks.java", "NabuItems.java"):
        registry += open(os.path.join(reg_dir, f), encoding="utf-8").read()
    for entry in data["palette"]:
        name = entry["Name"]
        if name.startswith("nabu:"):
            check(f'"{name.split(":")[1]}"' in registry, f"palette references unregistered {name}")

    print(f"screws needed: {len(screws)}   beds: {len(beds)}   markers: {len(markers)}   "
          f"terrace levels: {terrace_levels}")
    print(f"palette: {sorted({e['Name'] for e in data['palette']})}")

    plot, pgrid, ptiles = load("bed_plot")
    print(f"bed_plot {plot['size']}  blocks={len(plot['blocks'])}")
    check(any(t.get("id") == "minecraft:jigsaw" for t in ptiles.values()), "bed_plot has no jigsaw")
    check(any(t.get("metadata") == "nabu:beds" for t in ptiles.values()), "bed_plot has no bed marker")

    if failures:
        print("\nFAILED:")
        for f in failures:
            print("  -", f)
        return 1
    print("\nOK - every bed is boostable, watered, claimed by a marker, and within shrine reach.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
