"""Generate the mod's procedural block textures.

    python tools/generate_textures.py

Only textures that are *noise* belong here -- ones with no drawn structure, where a seed and a
palette say everything a hand-placed pixel would. Anything with a motif in it is drawn, not
generated, and lives in assets/ as its own file.

Palettes are taken from the textures already in the pack rather than invented, so a generated
tile sits in the same world as the drawn ones.
"""
import os

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "common", "src", "main", "resources", "assets", "nabu", "textures", "block")

SIZE = 16

# The four tones every dead thing in the pack is built from -- read straight out of
# withered_shrub, withered_vine and dead_leaves -- plus one step darker than any of them, for
# the cracks. Dried moss is the same dead brown as the foliage, laid flat.
DEEP = (78, 60, 40, 255)
DARK = (90, 69, 46, 255)
MID = (110, 85, 56, 255)
LIGHT = (133, 105, 66, 255)
PALE = (156, 128, 84, 255)

LADDER = [DEEP, DARK, MID, LIGHT, PALE]


def rnd(*vals):
    """The same FNV-1a the structure generator uses, so both read the same way."""
    h = 2166136261
    for v in vals:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        h ^= h >> 15
    return (h >> 8) % 1000


def step(tone, by):
    return LADDER[max(0, min(len(LADDER) - 1, LADDER.index(tone) + by))]


def dead_moss():
    """A dried crust: clumped, strandy, and flat enough to read as ground rather than leaves.

    Three scales of noise, because one is dirt and two is static. A 4x4 grid lays the clumps --
    moss dies back in patches, not evenly -- sheared sideways per row band so their edges are
    not the axis-aligned rectangles a raw grid gives. Two-pixel vertical strands run over the
    top, which is what separates moss from soil at this resolution: the growth still has a
    direction even after it has died. Per-pixel grain frays everything, and sparse cracks and
    bleached flecks finish it.

    Every scale has a period that divides 16 and the shear is taken modulo the tile, so the
    pattern meets itself where it wraps -- this is laid over whole terraces at a time.
    """
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    for x in range(SIZE):
        for y in range(SIZE):
            shear = rnd(y // 4, 7) % SIZE
            clump = rnd(((x + shear) % SIZE) // 4, y // 4, 11)
            if clump < 320:
                tone = DARK
            elif clump > 780:
                tone = LIGHT
            else:
                tone = MID

            # Strands, two pixels tall: the direction the growth had before it dried.
            if rnd(x, y // 2, 31) < 150:
                tone = step(tone, 1)

            grain = rnd(x, y, 13)
            if grain < 190:
                tone = step(tone, -1)
            elif grain > 880:
                tone = step(tone, 1)

            # Cracks in the crust, and the bleached tips that catch the light on top of it.
            if rnd(x, y, 23) < 55:
                tone = DEEP
            elif rnd(x, y, 29) < 35:
                tone = PALE

            pixels[x, y] = tone
    return image


TEXTURES = {"dead_moss": dead_moss}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, draw in sorted(TEXTURES.items()):
        path = os.path.join(OUT, name + ".png")
        image = draw()
        image.save(path)
        print("%-12s %dx%d -> %s" % (name, image.width, image.height, path))


if __name__ == "__main__":
    main()
