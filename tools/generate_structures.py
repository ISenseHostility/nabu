"""Generate the Hanging Gardens structure templates.

    python tools/generate_structures.py

The Wonder is a four-tier ziggurat, 33x33 at the base, assembled by the jigsaw from five
stacked pool pieces. Each piece is a full-footprint horizontal slab, so their bounding boxes
are disjoint in Y and can never reject one another:

    plinth      y 0..3    mass + cistern + reservoir court + shrine
    terrace_1   y 4..7    terrace A deck (y=4) + tier 2 mass
    terrace_2   y 8..11   terrace B deck (y=8) + tier 3 mass
    terrace_3   y 12..15  terrace C deck (y=12) + tier 4 mass
    summit      y 16..19  summit deck (y=16) + the ruined cella

The geometry is not arbitrary: it is derived from the gameplay constants in `common`, and
those are tight. Getting a bed to BOOSTED requires all of

  * a running screw within  |dx| <= 4, |dy| <= 1, |dz| <= 4   (PlantingBedBlock.BOOST_*)
  * water within            |dx| <= 4, dy in {0, +1}, |dz| <= 4   (PlantingBedBlock.WATER_RADIUS)

and a screw only ever draws from the block directly beneath it and delivers to the block
directly above, so a lift is necessarily a vertical column and a chain of lifts is one
continuous column at a single (x, z). That forces each terrace to own a shaft rising out of
the cistern at y=2, and its planting to sit in a bay hugging that shaft. Bed decks land on
even levels so that water, which surfaces on odd ones from a y=2 cistern, arrives exactly on
the bed layer: screws at y=3,5,7..., water at y=4,6,8...

Screw budget: the three shafts need 1, 3 and 5 screws bottom to top, nine in total, against
two chests of 5-7 each.

Each buried shaft has a maintenance well two cells along the channel row, separated from the
shaft by a divider that is solid at water levels and open at screw levels. That is what lets
a player place a screw sideways from the well while the delivered sources stay sealed in
brick and never spill down the stairs.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "data", "nabu", "structure", "hanging_gardens")

DATA_VERSION = 4903

SIZE_X, SIZE_Y, SIZE_Z = 33, 20, 33

# (x0, x1, z0, z1, deck) innermost first. `deck` is the y of the surface layer, which is
# itself solid -- the mass runs 0..deck inclusive.
TIERS = [
    (12, 20, 12, 20, 16),
    (8, 24, 8, 24, 12),
    (4, 28, 4, 28, 8),
    (0, 32, 0, 32, 4),
]

# (name, y0, y1, variants)
SLABS = [
    ("plinth", 0, 3, 1),
    ("terrace_1", 4, 7, 2),
    ("terrace_2", 8, 11, 2),
    ("terrace_3", 12, 15, 2),
    ("summit", 16, 19, 2),
]

CISTERN_Y = 2

# One straight processional ramp up the south face: ground at z=32, summit at z=17.
STAIR_X0, STAIR_X1 = 14, 18
STAIR_Z0, STAIR_Z1 = 17, 32

COURT_X0, COURT_X1, COURT_Z0, COURT_Z1 = 12, 20, 1, 2
SHRINE_POS = (16, 4, 3)
CHEST_POSITIONS = [(14, 4, 3), (18, 4, 3)]

CELLA_X0, CELLA_X1, CELLA_Z0, CELLA_Z1 = 13, 19, 12, 16
CELLA_DOOR = (16, CELLA_Z1)

MARKER_METADATA = "nabu:beds"

BRICK = ("nabu:babylonian_bricks", None)
CRACKED = ("nabu:cracked_babylonian_bricks", None)
MOSSY = ("nabu:mossy_babylonian_bricks", None)
GLAZED = ("nabu:glazed_babylonian_bricks", None)
CHISELED = ("nabu:chiseled_babylonian_bricks", None)
TILES = ("nabu:babylonian_tiles", None)
MOSSY_TILES = ("nabu:mossy_babylonian_tiles", None)
PARAPET = ("nabu:babylonian_brick_wall", None)
STAIR = ("nabu:babylonian_brick_stairs", {"facing": "north", "half": "bottom", "shape": "straight"})
AIR = ("minecraft:air", None)
WATER = ("minecraft:water", {"level": "0"})
BED = ("nabu:planting_bed", {"moisture": "0", "tier": "dry"})
SHRINE = ("nabu:garden_controller", {"powered": "false"})
SHRUB = ("nabu:withered_shrub", None)
LEAVES = ("nabu:dead_leaves", None)
LADDER = ("minecraft:ladder", {"facing": "east", "waterlogged": "false"})


def bay(deck, shaft, wall, well, channel, beds, marker):
    return {
        "deck": deck, "shaft": shaft, "wall": wall, "well": well,
        "channel": channel, "beds": beds, "marker": marker,
    }


# Terrace A hangs off the west band, B off the east, C off the north, so restoring the
# Gardens walks the player right around the monument.
BAYS = [
    bay(
        deck=4, shaft=(2, 10), wall=None, well=None,
        channel=[(2, z) for z in range(6, 15)],
        beds=[(x, z) for x in (1, 3) for z in range(6, 15)],
        marker=(2, 5, 10),
    ),
    bay(
        deck=8, shaft=(27, 12), wall=(27, 11), well=(27, 10),
        channel=[(27, z) for z in range(12, 17)],
        beds=[(x, z) for x in (25, 26) for z in range(8, 17)],
        marker=(27, 9, 12),
    ),
    bay(
        deck=12, shaft=(16, 9), wall=(15, 9), well=(14, 9),
        channel=[(x, 9) for x in range(16, 21)],
        beds=[(x, z) for x in range(12, 21) for z in (10, 11)],
        marker=(16, 13, 9),
    ),
]


def rnd(*vals):
    h = 2166136261
    for v in vals:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        h ^= h >> 15
    return (h >> 8) % 1000


def in_stair(x, z):
    return STAIR_X0 <= x <= STAIR_X1 and STAIR_Z0 <= z <= STAIR_Z1


def tier_deck(x, z):
    """Deck of the tier this column belongs to, ignoring the stairway trench."""
    for x0, x1, z0, z1, d in TIERS:
        if x0 <= x <= x1 and z0 <= z <= z1:
            return d
    return None


def deck_at(x, z):
    """Top solid level of this column, stairway included."""
    if in_stair(x, z):
        return 33 - z
    return tier_deck(x, z)


def is_landing(x, z):
    """Where the ramp runs flush with a terrace, so the tread is paving rather than a step."""
    return in_stair(x, z) and 33 - z == tier_deck(x, z)


class Volume:
    """The whole monument in one grid, sliced into jigsaw pieces afterwards."""

    def __init__(self):
        self.cells = {}
        self.tiles = {}

    def set(self, x, y, z, block, tile=None):
        if not (0 <= x < SIZE_X and 0 <= y < SIZE_Y and 0 <= z < SIZE_Z):
            return
        self.cells[(x, y, z)] = block
        if tile is not None:
            self.tiles[(x, y, z)] = tile
        else:
            self.tiles.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.cells.get((x, y, z))

    def solid(self, x, y, z):
        return self.get(x, y, z) not in (None, AIR, WATER)


def weathered(x, y, z, variant):
    """Hand-scatter a little decay. The rule processor adds more on top of this."""
    r = rnd(x, y, z, 5, variant)
    if r < 90:
        return CRACKED
    if r < 150:
        return MOSSY
    return BRICK


def paving(x, z, variant):
    return MOSSY_TILES if rnd(x, z, 23, variant) < 180 else TILES


def protected_cells():
    """Everything the bays own. Decoration and paving must not touch these."""
    keep = set()
    for b in BAYS:
        for spot in (b["shaft"], b["wall"], b["well"]):
            if spot:
                keep.add(spot)
        keep.update(b["channel"])
        keep.update(b["beds"])
    return keep


def fill_mass(v, variant):
    """Foundation, cistern, tier masses and the decks that cap them."""
    protected = protected_cells()
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            deck = deck_at(x, z)
            for y in range(0, deck + 1):
                if y == deck:
                    if in_stair(x, z) and not is_landing(x, z):
                        v.set(x, y, z, STAIR)
                    elif (x, z) in protected:
                        v.set(x, y, z, BRICK)      # bays rewrite their own decks below
                    else:
                        v.set(x, y, z, paving(x, z, variant))
                elif y == CISTERN_Y and 1 <= x <= 31 and 1 <= z <= 31 and not in_stair(x, z):
                    v.set(x, y, z, WATER)
                else:
                    v.set(x, y, z, weathered(x, y, z, variant))
            for y in range(deck + 1, SIZE_Y):
                v.set(x, y, z, AIR)


def cut_court(v):
    """Sunken basin in the north band, opening the cistern in front of the shrine."""
    for x in range(COURT_X0, COURT_X1 + 1):
        for z in range(COURT_Z0, COURT_Z1 + 1):
            v.set(x, 3, z, AIR)
            v.set(x, 4, z, AIR)


def data_marker():
    return {
        "id": "minecraft:structure_block",
        "mode": "DATA",
        "metadata": MARKER_METADATA,
        "name": "", "author": "", "rotation": "NONE", "mirror": "NONE",
        "integrity": 1.0, "seed": 0, "ignoreEntities": 1, "powered": 0,
        "showair": 0, "showboundingbox": 0,
        "posX": 0, "posY": 0, "posZ": 0, "sizeX": 0, "sizeY": 0, "sizeZ": 0,
    }


def build_bays(v, variant):
    """Shafts, dividers, maintenance wells, channels and the planting itself."""
    for index, b in enumerate(BAYS):
        deck = b["deck"]
        sx, sz = b["shaft"]

        # The lift column: air from the first screw level up to where water must surface,
        # standing directly on the cistern so the bottom screw has a real source to draw on.
        for y in range(3, deck + 1):
            v.set(sx, y, sz, AIR)
        v.set(sx, CISTERN_Y, sz, WATER)

        backing = None
        if b["well"]:
            wx, wz = b["well"]
            dx, dz = b["wall"]
            for y in range(3, deck + 1):
                # Divider solid at water levels, open at screw levels. Sealing the even
                # courses keeps a delivered source from pouring into the well the player is
                # standing in; opening the odd ones is what lets them place at all.
                v.set(dx, y, dz, AIR if y % 2 else weathered(dx, y, dz, variant))
                v.set(wx, y, wz, LADDER)
            # The well is not a way into the cistern.
            v.set(wx, CISTERN_Y, wz, BRICK)
            # The ladder needs something solid behind it the whole way up, so the cell it
            # hangs off stays paving rather than becoming planting.
            backing = (wx - 1, wz)
            v.set(wx - 1, deck, wz, paving(wx - 1, wz, variant))

        for cx, cz in b["channel"]:
            v.set(cx, deck, cz, AIR)

        skipped = 0
        for bx, bz in b["beds"]:
            if backing and (bx, bz) == backing:
                continue
            # Variant B leaves a few plots collapsed, so the bed count really is discovered.
            if variant and skipped < 4 and rnd(bx, bz, index, 41) < 140:
                v.set(bx, deck, bz, paving(bx, bz, variant))
                skipped += 1
                continue
            v.set(bx, deck, bz, BED)

        # A ring of glazed brick around the shaft mouth, so the socket reads at a glance.
        for ox in (-1, 0, 1):
            for oz in (-1, 0, 1):
                if (ox or oz) and v.get(sx + ox, deck, sz + oz) == BRICK:
                    v.set(sx + ox, deck, sz + oz, GLAZED)

        mx, my, mz = b["marker"]
        v.set(mx, my, mz, ("minecraft:structure_block", {"mode": "data"}), tile=data_marker())


def build_parapets(v, variant):
    """A course of wall along the outer lip of every terrace, broken where it has weathered."""
    for x0, x1, z0, z1, deck in TIERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x0 < x < x1 and z0 < z < z1:
                    continue                       # interior, not the lip
                if in_stair(x, z) or not v.solid(x, deck, z):
                    continue
                if rnd(x, z, 31, variant) < 130:
                    continue                       # a gap in the parapet
                v.set(x, deck + 1, z, PARAPET)


def build_shrine(v):
    sx, sy, sz = SHRINE_POS
    v.set(sx, sy, sz, SHRINE)
    v.set(sx, sy - 1, sz, CHISELED)
    for cx, cy, cz in CHEST_POSITIONS:
        v.set(cx, cy, cz, ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"}),
              tile={"id": "minecraft:chest", "LootTable": "nabu:chests/hanging_gardens"})
        v.set(cx, cy - 1, cz, CHISELED)


def build_cella(v, variant):
    """The roofless shrine house on the summit. Decorative only -- nothing gates on it."""
    deck = 16
    for x in range(CELLA_X0, CELLA_X1 + 1):
        for z in range(CELLA_Z0, CELLA_Z1 + 1):
            edge = x in (CELLA_X0, CELLA_X1) or z in (CELLA_Z0, CELLA_Z1)
            doorway = (x, z) == CELLA_DOOR
            if not edge or doorway:
                continue
            height = 3 if rnd(x, z, 53, variant) > 220 else 2
            for y in range(deck + 1, deck + 1 + height):
                v.set(x, y, z, weathered(x, y, z, variant))
    v.set(16, deck + 1, 14, CHISELED)
    v.set(16, deck + 2, 14, GLAZED)


def decorate(v, variant):
    """Dead growth, scattered deterministically. Only ever into air with something to hold it."""
    reserved = set(protected_cells())
    for cx, _, cz in CHEST_POSITIONS + [SHRINE_POS]:
        for ox in (-1, 0, 1):
            for oz in (-1, 0, 1):
                reserved.add((cx + ox, cz + oz))
    # The temple doorway is the only way into the monument's interior. A shrub growing in it
    # would be a one-block plug across the entrance to everything below.
    reserved.add(CELLA_DOOR)

    # Canopy overhanging every terrace edge, with vines trailing beneath it.
    #
    # The drape is confined to the top two courses of each riser on purpose. Dead leaves are
    # full blocks, and filling the riser to the floor would wall off the terrace below; kept
    # overhead it reads as growth spilling off the terrace above instead, and head height stays
    # clear to walk through. This is where most of the ruin's foliage lives, so the greening
    # sweep has a canopy to bring back rather than a sprinkling.
    for x0, x1, z0, z1, deck in TIERS[:-1]:
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x0 <= x <= x1 and z0 <= z <= z1:
                    continue
                below = deck_at(x, z)
                if below is None or below >= deck:
                    continue
                side = None
                if x == x0 - 1 and z0 <= z <= z1:
                    side = "east"
                elif x == x1 + 1 and z0 <= z <= z1:
                    side = "west"
                elif z == z0 - 1 and x0 <= x <= x1:
                    side = "south"
                elif z == z1 + 1 and x0 <= x <= x1:
                    side = "north"
                if side is None or (x, z) in reserved or in_stair(x, z):
                    continue
                for y in range(below + 1, deck + 1):
                    if v.get(x, y, z) != AIR:
                        continue
                    if y >= deck - 1:
                        if rnd(x, y, z, 13, variant) < 620:
                            v.set(x, y, z, LEAVES)
                    elif rnd(x, y, z, 11, variant) < 450:
                        props = {"up": "false", "north": "false", "south": "false",
                                 "east": "false", "west": "false"}
                        props[side] = "true"
                        v.set(x, y, z, ("nabu:withered_vine", props))

    # Foliage cresting the parapets, so the silhouette breaks up when read from below.
    for x0, x1, z0, z1, deck in TIERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x0 < x < x1 and z0 < z < z1:
                    continue
                if (x, z) in reserved or in_stair(x, z):
                    continue
                if v.get(x, deck + 1, z) == PARAPET and v.get(x, deck + 2, z) == AIR \
                        and rnd(x, z, 29, variant) < 300:
                    v.set(x, deck + 2, z, LEAVES)

    # Undergrowth on the terrace decks, kept off the planting and the ceremony. Shrubs carry
    # the bulk of it: they revive into ferns and, unlike leaves, cost nothing to walk through.
    for x in range(SIZE_X):
        for z in range(SIZE_Z):
            if (x, z) in reserved or in_stair(x, z):
                continue
            deck = deck_at(x, z)
            if deck is None or not v.solid(x, deck, z) or v.get(x, deck + 1, z) != AIR:
                continue
            r = rnd(x, z, 17, variant)
            if r < 340:
                v.set(x, deck + 1, z, SHRUB)
            elif r < 450:
                # One block, sitting on the ground. Stacking two reads as a floating pillar
                # rather than as fallen canopy.
                v.set(x, deck + 1, z, LEAVES)


# --- the interior ---------------------------------------------------------------------------
#
# Chambers are carved into the tier masses rather than attached as their own pool pieces, and
# that is forced rather than chosen: every terrace piece is a full-footprint 33x4x33 slab, and
# vanilla refuses any child whose bounding box overlaps a piece already placed. A room piece
# sitting inside tier 3's slab would be rejected every single time. The pool variants carry the
# variety instead.
#
# One spiral winds down a 3x3 well from the cella floor to the great hall, twelve steps and one
# and a half turns, landing on each chamber floor on the way. It is threaded between all three
# lift shafts and the processional ramp: no chamber wall doubles as a shaft wall, so nothing in
# here can breach a water seal.

NEWEL = (16, 14)

# The eight cells the stair winds through, in descending order. Step k uses cell k % 8 and
# drops one course, so a full turn costs eight blocks and clears seven overhead.
STAIR_CYCLE = [(16, 15), (15, 15), (15, 14), (15, 13), (16, 13), (17, 13), (17, 14), (17, 15)]
STAIR_TOP_Y = 15
STAIR_STEPS = 12

# (name, x0, x1, z0, z1, floor). Air runs floor+1 .. floor+3; the course above that is the
# chamber's ceiling and belongs to the slab overhead.
# Each south edge stops one cell short of where the processional ramp's tread sits at that
# height. A tread is a stair block, not a full cube, so a wall built on that course would leak
# sight and skylight straight into the ramp trench. Ending on the ramp's landing course instead
# gives every chamber a wall of solid mass.
CHAMBERS = [
    ("antechamber", 13, 19, 13, 16, 12),
    ("hall", 9, 23, 11, 20, 8),
    ("great_hall", 5, 25, 11, 24, 4),
]

# (x0, x1, z0, z1, floor) -- partitions raised inside a chamber after it is hollowed out.
CHAMBER_WALLS = [
    (13, 13, 11, 20, 8),
    (19, 19, 11, 20, 8),
    (11, 11, 11, 24, 4),
    (19, 19, 11, 24, 4),
    (5, 10, 18, 18, 4),
    (20, 25, 18, 18, 4),
]

# (x, z, floor) -- two courses knocked back out of a partition.
DOORWAYS = [
    (13, 16, 8), (19, 16, 8),
    (11, 14, 4), (11, 22, 4), (19, 14, 4), (19, 22, 4),
    (7, 18, 4), (23, 18, 4),
]

PILLARS = [(x, z, 4) for x in (13, 17) for z in (11, 18, 21, 24)]

# (x, y, z, facing). Their own table on purpose: the surface chests are what the lift puzzle is
# budgeted against, and dropping more water screws down here would dissolve that constraint.
CHAMBER_CHESTS = [
    (18, 13, 16, "west"),
    (22, 9, 16, "west"),
    (7, 5, 13, "south"),
    (23, 5, 22, "north"),
]

CHAMBER_LOOT = "nabu:chests/hanging_gardens_chamber"


def stair_treads():
    """(cell, floor y) for every step, top to bottom."""
    return [(STAIR_CYCLE[k % len(STAIR_CYCLE)], STAIR_TOP_Y - k) for k in range(STAIR_STEPS)]


def stair_facing(k):
    """Raised half toward the step above, so the walk is smooth in both directions.

    Step 0 is the exception: nothing is above it but the cella floor, and the player arrives
    through the doorway to its south.
    """
    if k == 0:
        return "south"
    cx, cz = STAIR_CYCLE[k % len(STAIR_CYCLE)]
    px, pz = STAIR_CYCLE[(k - 1) % len(STAIR_CYCLE)]
    if px > cx:
        return "east"
    if px < cx:
        return "west"
    return "south" if pz > cz else "north"


def build_interior(v, variant):
    for _, x0, x1, z0, z1, floor in CHAMBERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v.set(x, floor, z, paving(x, z, variant))
                for y in range(floor + 1, floor + 4):
                    v.set(x, y, z, AIR)

    for x0, x1, z0, z1, floor in CHAMBER_WALLS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(floor + 1, floor + 4):
                    v.set(x, y, z, weathered(x, y, z, variant))

    for x, z, floor in DOORWAYS:
        for y in (floor + 1, floor + 2):
            v.set(x, y, z, AIR)

    for x, z, floor in PILLARS:
        for y in range(floor + 1, floor + 4):
            v.set(x, y, z, CHISELED if y == floor + 3 else weathered(x, y, z, variant))

    build_stairwell(v, variant)

    for x, y, z, facing in CHAMBER_CHESTS:
        v.set(x, y, z, ("minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"}),
              tile={"id": "minecraft:chest", "LootTable": CHAMBER_LOOT})

    furnish_chambers(v, variant)


def build_stairwell(v, variant):
    treads = stair_treads()

    # Two courses of clearance over each tread and not one more. Carving the whole well would
    # punch a ring of holes through every chamber floor the stair passes; this way the only
    # openings are the ones the descent actually needs.
    for (cx, cz), y in treads:
        v.set(cx, y + 1, cz, AIR)
        v.set(cx, y + 2, cz, AIR)

    for k, ((cx, cz), y) in enumerate(treads):
        v.set(cx, y, cz, ("nabu:babylonian_brick_stairs",
                          {"facing": stair_facing(k), "half": "bottom", "shape": "straight"}))

    # The newel the stair winds around. Re-laid after the chambers, which hollow straight
    # through it, and carried up to the cella floor so the altar stands on its head.
    nx, nz = NEWEL
    for y in range(4, 16):
        v.set(nx, y, nz, weathered(nx, y, nz, variant))


def furnish_chambers(v, variant):
    """Dead growth indoors too, so the greening sweep has something to bring back down here."""
    # The seam column carries a jigsaw block in every slab. Whatever stands on it would be
    # resting on that block rather than on the floor the template appears to show.
    #
    # Doorways are only two courses tall, so one dead-leaf block -- a full cube -- growing in
    # one seals the room behind it outright.
    occupied = set(STAIR_CYCLE) | {NEWEL, (SEAM_X, SEAM_Z)}
    occupied.update((x, z) for x, z, _ in DOORWAYS)

    for _, x0, x1, z0, z1, floor in CHAMBERS:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in occupied or not v.solid(x, floor, z):
                    continue
                if v.get(x, floor + 1, z) != AIR:
                    continue                      # a partition, pillar or chest stands here

                # Vines only where there is a wall to hang them on, checked against the
                # chamber's own ceiling course so they never float mid-room.
                hung = False
                for side, nx, nz in (("east", x - 1, z), ("west", x + 1, z),
                                     ("south", x, z - 1), ("north", x, z + 1)):
                    if not v.solid(nx, floor + 2, nz):
                        continue
                    if rnd(x, z, 67, variant) < 200:
                        props = {"up": "false", "north": "false", "south": "false",
                                 "east": "false", "west": "false"}
                        props[side] = "true"
                        v.set(x, floor + 2, z, ("nabu:withered_vine", props))
                        hung = True
                    break

                r = rnd(x, z, 61 + floor, variant)
                if r < 240:
                    v.set(x, floor + 1, z, SHRUB)
                elif r < 330 and not hung:
                    v.set(x, floor + 1, z, LEAVES)


def build(variant):
    v = Volume()
    fill_mass(v, variant)
    cut_court(v)
    build_bays(v, variant)
    build_interior(v, variant)
    build_parapets(v, variant)
    build_shrine(v)
    build_cella(v, variant)
    decorate(v, variant)
    return v


# --- jigsaw assembly -----------------------------------------------------------------------
#
# One connector per seam, dead centre of the monument at (16, ., 16), where every slab is
# solid tier mass. The parent socket faces up and the child faces down at the course directly
# above it, which pins the child's origin to exactly one offset. `aligned` with a matching top
# facing locks the child's rotation to the parent's as well, so a tier can never land turned.
#
# The slabs are y-disjoint over the same 33x33 footprint, so no two piece bounding boxes can
# ever intersect and be rejected. That is the whole safety argument for assembling the tiers
# this way, and it is why every piece stays a full-footprint slab rather than just its tier.

LINKS = [
    ("plinth", "terrace_1"),
    ("terrace_1", "terrace_2"),
    ("terrace_2", "terrace_3"),
    ("terrace_3", "summit"),
]

SEAM_X, SEAM_Z = 16, 16


def jigsaw_tile(name, target, pool, final_state):
    return {
        "id": "minecraft:jigsaw",
        "name": name,
        "target": target,
        "pool": pool,
        "final_state": final_state,
        "joint": "aligned",
    }


def slab_jigsaws(v, slab_name, y0, y1):
    """(pos, state, tile) for the seams this slab takes part in."""
    out = []
    for parent, child in LINKS:
        if parent == slab_name:
            block = v.get(SEAM_X, y1, SEAM_Z)
            out.append(((SEAM_X, y1, SEAM_Z),
                        ("minecraft:jigsaw", {"orientation": "up_north"}),
                        jigsaw_tile("nabu:%s_top" % parent, "nabu:%s_base" % child,
                                    "nabu:hanging_gardens/%s" % child, block[0])))
        if child == slab_name:
            block = v.get(SEAM_X, y0, SEAM_Z)
            out.append(((SEAM_X, y0, SEAM_Z),
                        ("minecraft:jigsaw", {"orientation": "down_north"}),
                        jigsaw_tile("nabu:%s_base" % child, "nabu:%s_top" % parent,
                                    "minecraft:empty", block[0])))
    return out


def slab_to_nbt(v, y0, y1, jigsaws):
    cells = dict(((x, y, z), b) for (x, y, z), b in v.cells.items() if y0 <= y <= y1)
    tiles = dict(((x, y, z), t) for (x, y, z), t in v.tiles.items() if y0 <= y <= y1)
    for pos, state, tile in jigsaws:
        cells[pos] = state
        tiles[pos] = tile

    palette, index, blocks = [], {}, []
    for (x, y, z), (name, props) in sorted(cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
        key = (name, tuple(sorted(props.items())) if props else ())
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            palette.append(entry)
        # `pos` is a TAG_List of ints, not an int array -- that is what vanilla writes.
        entry = {"pos": [x, y - y0, z], "state": index[key]}
        tile = tiles.get((x, y, z))
        if tile:
            entry["nbt"] = tile
        blocks.append(entry)

    return {
        "DataVersion": DATA_VERSION,
        "size": [SIZE_X, y1 - y0 + 1, SIZE_Z],
        "palette": palette,
        "blocks": blocks,
        "entities": [],
    }


def piece_names(slab_name, variants):
    if variants == 1:
        return [slab_name]
    return ["%s_%s" % (slab_name, chr(ord("a") + i)) for i in range(variants)]


def main():
    os.makedirs(OUT, exist_ok=True)
    volumes = {}
    for slab_name, y0, y1, variants in SLABS:
        for variant, piece in enumerate(piece_names(slab_name, variants)):
            if variant not in volumes:
                volumes[variant] = build(variant)
            v = volumes[variant]
            data = slab_to_nbt(v, y0, y1, slab_jigsaws(v, slab_name, y0, y1))
            path = os.path.join(OUT, piece + ".nbt")
            nbt.write(path, data)
            beds = sum(1 for b in data["blocks"] if data["palette"][b["state"]]["Name"] == "nabu:planting_bed")
            print("%-12s size=%s blocks=%5d palette=%3d beds=%2d -> %s"
                  % (piece, data["size"], len(data["blocks"]), len(data["palette"]), beds, path))


if __name__ == "__main__":
    main()
