"""Generate the Hanging Gardens structure templates.

    python tools/generate_structures.py

This is a **scaffold**, not the finished Wonder. It exists so the structure generates and the
whole chain -- advancements, the tablet in the chest, the greening sweep, the weathering
processor -- can actually be exercised in-game. Refine it in creative and re-export over the top.

The geometry is not arbitrary: it is derived from the gameplay constants in `common`, and those
constants are tight. Getting a bed to BOOSTED requires all of

  * a running screw within  |dx| <= 4, |dy| <= 1, |dz| <= 4   (PlantingBedBlock.BOOST_*)
  * water within            |dx| <= 4, dy in {0, +1}, |dz| <= 4   (PlantingBedBlock.WATER_RADIUS)

and a screw only ever draws from the block directly beneath it and delivers to the block directly
above, so a lift is necessarily a vertical column. Together those force the layout below: each
terrace gets its **own** shaft rising out of the cistern, and its planting sits in a 7x7 plot
centred on that shaft. A single shared shaft cannot work -- the terrace above would have to sit on
top of the terrace below's planting area, which is inside the same 9x9 boost box.

Screw budget: shafts need 1, 2 and 3 screws bottom to top, six in total, against two chests of
4-6 each.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "data", "nabu", "structure", "hanging_gardens")

DATA_VERSION = 4903

SIZE_X, SIZE_Y, SIZE_Z = 25, 11, 25

BRICK = ("nabu:babylonian_bricks", None)
CRACKED = ("nabu:cracked_babylonian_bricks", None)
MOSSY = ("nabu:mossy_babylonian_bricks", None)
GLAZED = ("nabu:glazed_babylonian_bricks", None)
CHISELED = ("nabu:chiseled_babylonian_bricks", None)
AIR = ("minecraft:air", None)
WATER = ("minecraft:water", {"level": "0"})
BED = ("nabu:planting_bed", {"moisture": "0", "tier": "dry"})
SHRINE = ("nabu:garden_controller", {"powered": "false"})
SHRUB = ("nabu:withered_shrub", None)
LEAVES = ("nabu:dead_leaves", None)

# (shaft x, shaft z, bed level). Water surfaces at the bed level; the screw sits one below it.
TERRACES = [
    (4, 12, 3),
    (12, 12, 5),
    (20, 12, 7),
]
PLOT_RADIUS = 3          # 7x7 plot, comfortably inside the 4-block boost box
MARKER_METADATA = "nabu:beds"

SHRINE_POS = (12, 5, 6)
CHEST_POSITIONS = [(10, 5, 6), (14, 5, 6)]
BASIN = (9, 15, 2, 5)    # x0, x1, z0, z1 -- cut down to expose the cistern in front of the shrine


def terrace_top(x):
    """Top solid level of the stepped mass at a given x. The steps rise from west to east."""
    if x <= 8:
        return 2
    if x <= 16:
        return 4
    return 6


def rnd(*vals):
    h = 2166136261
    for v in vals:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        h ^= h >> 15
    return (h >> 8) % 1000


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.cells = {}
        self.tiles = {}

    def set(self, x, y, z, block, tile=None):
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]):
            return
        self.cells[(x, y, z)] = block
        if tile is not None:
            self.tiles[(x, y, z)] = tile
        else:
            self.tiles.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def to_nbt(self):
        palette, index = [], {}
        blocks = []
        for (x, y, z), (name, props) in sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            key = (name, tuple(sorted(props.items())) if props else ())
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(props)
                palette.append(entry)
            # `pos` is a TAG_List of ints, not an int array -- that is what vanilla writes.
            entry = {"pos": [x, y, z], "state": index[key]}
            tile = self.tiles.get((x, y, z))
            if tile:
                entry["nbt"] = tile
            blocks.append(entry)
        return {
            "DataVersion": DATA_VERSION,
            "size": list(self.size),
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        }


def weathered(x, y, z):
    """Hand-scatter a little decay. The rule processor adds more on top of this."""
    r = rnd(x, y, z, 5)
    if r < 90:
        return CRACKED
    if r < 150:
        return MOSSY
    return BRICK


def build_skeleton():
    t = Template(SIZE_X, SIZE_Y, SIZE_Z)

    # --- the mass: foundation, cistern, and the stepped fill above it -------------------------
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            edge = x in (0, SIZE_X - 1) or z in (0, SIZE_Z - 1)
            t.set(x, 0, z, weathered(x, 0, z))
            # The cistern. Every shaft draws from this, so it must reach under all three.
            t.set(x, 1, z, BRICK if edge else WATER)
            top = terrace_top(x)
            for y in range(2, top + 1):
                t.set(x, y, z, weathered(x, y, z))
            for y in range(top + 1, SIZE_Y):
                t.set(x, y, z, AIR)

    # --- reservoir basin in front of the shrine ------------------------------------------------
    bx0, bx1, bz0, bz1 = BASIN
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            for y in range(2, terrace_top(x) + 1):
                t.set(x, y, z, AIR)

    # --- terraces --------------------------------------------------------------------------
    for sx, sz, bed_y in TERRACES:
        top = terrace_top(sx)

        # A ring of glazed brick around the shaft mouth, so the socket is findable at a glance.
        # Laid before the carving below, not after: it covers the mouth of the access column too,
        # and capping that would leave the shaft bottom unreachable again.
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz:
                    t.set(sx + dx, top, sz + dz, GLAZED)

        # Shaft: a clear column from the cistern up to where the water must surface. The player
        # drops screws down it; the lowest one takes its intake straight off the cistern.
        for y in range(2, bed_y + 1):
            t.set(sx, y, sz, AIR)

        # A second column alongside it, purely so the shaft is reachable. The top shaft is five
        # deep, and placing a screw at the bottom of a 1x1 well means clicking a wall you can only
        # see edge-on from directly above. With this the player drops in and places sideways.
        for y in range(2, bed_y):
            t.set(sx, y, sz + 1, AIR)

        # Planting plot: beds everywhere except a cross of open channel, which is what the
        # delivered source actually flows along.
        for dx in range(-PLOT_RADIUS, PLOT_RADIUS + 1):
            for dz in range(-PLOT_RADIUS, PLOT_RADIUS + 1):
                x, z = sx + dx, sz + dz
                if dx == 0 and dz == 0:
                    continue                       # the shaft mouth itself
                if dx == 0 or dz == 0:
                    t.set(x, bed_y, z, AIR)        # channel
                else:
                    t.set(x, bed_y, z, BED)

        # Data marker. One per terrace, centred, one above the beds: BedMarkers reaches 4 out
        # and 2 up/down, which covers the whole plot and cannot touch a neighbouring terrace.
        t.set(sx, bed_y + 1, sz, ("minecraft:structure_block", {"mode": "data"}), tile={
            "id": "minecraft:structure_block",
            "mode": "DATA",
            "metadata": MARKER_METADATA,
            "name": "",
            "author": "",
            "rotation": "NONE",
            "mirror": "NONE",
            "integrity": 1.0,
            "seed": 0,
            "ignoreEntities": 1,
            "powered": 0,
            "showair": 0,
            "showboundingbox": 0,
            "posX": 0, "posY": 0, "posZ": 0,
            "sizeX": 0, "sizeY": 0, "sizeZ": 0,
        })

    # --- shrine and chests -------------------------------------------------------------------
    sx, sy, sz = SHRINE_POS
    t.set(sx, sy, sz, SHRINE)
    t.set(sx, sy - 1, sz, CHISELED)
    for i, (cx, cy, cz) in enumerate(CHEST_POSITIONS):
        t.set(cx, cy, cz, ("minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"}),
              tile={"id": "minecraft:chest", "LootTable": "nabu:chests/hanging_gardens"})

    decorate(t)
    return t


def decorate(t):
    """Dead growth, scattered deterministically. Only ever into air with something to hold it."""
    # Vines down the two risers, which are the tall exposed faces of the steps.
    for riser_x, low_top, high_top in ((8, 2, 4), (16, 4, 6)):
        for z in range(1, SIZE_Z - 1):
            for y in range(low_top + 1, high_top + 1):
                if rnd(riser_x, y, z, 11) < 420 and t.get(riser_x, y, z) == AIR:
                    t.set(riser_x, y, z, ("nabu:withered_vine", {
                        "up": "false", "north": "false", "south": "false",
                        "east": "true", "west": "false",
                    }))

    # Shrubs and dead canopy on the terrace surfaces, kept clear of the plots and the shrine.
    reserved = set()
    for sx, sz, bed_y in TERRACES:
        for dx in range(-PLOT_RADIUS - 1, PLOT_RADIUS + 2):
            for dz in range(-PLOT_RADIUS - 1, PLOT_RADIUS + 2):
                reserved.add((sx + dx, sz + dz))
    for cx, _, cz in CHEST_POSITIONS + [SHRINE_POS]:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                reserved.add((cx + dx, cz + dz))

    for x in range(1, SIZE_X - 1):
        for z in range(1, SIZE_Z - 1):
            if (x, z) in reserved:
                continue
            surface = terrace_top(x) + 1
            if t.get(x, surface, z) != AIR or t.get(x, surface - 1, z) in (AIR, WATER, None):
                continue
            r = rnd(x, z, 17)
            if r < 110:
                t.set(x, surface, z, SHRUB)
            elif r < 145:
                # One block, sitting on the ground. Stacking two reads as a floating pillar
                # rather than as fallen canopy.
                t.set(x, surface, z, LEAVES)


def build_bed_plot():
    """A spare variable-bed piece for the `nabu:hanging_gardens/terrace_beds` pool.

    Nothing references it yet -- the skeleton carries guaranteed beds instead, because an
    untested jigsaw connection is the one failure that would leave the Wonder with no planting at
    all. It is generated ready to wire: give the skeleton an up-facing jigsaw named
    `nabu:terrace_socket` whose pool is `nabu:hanging_gardens/terrace_beds`, and this piece's
    down-facing `nabu:bed_plot` jigsaw will meet it.
    """
    t = Template(7, 3, 7)
    for x in range(7):
        for z in range(7):
            t.set(x, 0, z, weathered(x, 40, z))
            # Cross of open channel through the middle, beds either side of it.
            t.set(x, 1, z, AIR if (x == 3 or z == 3) else BED)
            t.set(x, 2, z, AIR)

    t.set(3, 0, 3, ("minecraft:jigsaw", {"orientation": "down_south"}), tile={
        "id": "minecraft:jigsaw",
        "name": "nabu:bed_plot",
        "target": "nabu:terrace_socket",
        "pool": "minecraft:empty",
        "final_state": "minecraft:air",
        "joint": "rollable",
    })
    t.set(3, 2, 3, ("minecraft:structure_block", {"mode": "data"}), tile={
        "id": "minecraft:structure_block",
        "mode": "DATA",
        "metadata": MARKER_METADATA,
        "name": "", "author": "", "rotation": "NONE", "mirror": "NONE",
        "integrity": 1.0, "seed": 0, "ignoreEntities": 1, "powered": 0,
        "showair": 0, "showboundingbox": 0,
        "posX": 0, "posY": 0, "posZ": 0, "sizeX": 0, "sizeY": 0, "sizeZ": 0,
    })
    return t


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, template in (("skeleton", build_skeleton()), ("bed_plot", build_bed_plot())):
        path = os.path.join(OUT, name + ".nbt")
        data = template.to_nbt()
        nbt.write(path, data)
        beds = sum(1 for b in data["blocks"] if data["palette"][b["state"]]["Name"] == "nabu:planting_bed")
        print(f"{name:9s} size={data['size']} blocks={len(data['blocks']):5d} "
              f"palette={len(data['palette']):3d} beds={beds} -> {path}")


if __name__ == "__main__":
    main()
