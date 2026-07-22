package ai.jarno.nabu.block;

import net.minecraft.util.StringRepresentable;

/**
 * How alive a planting bed is.
 *
 * <p>The gradient is deliberate. Ordinary water gets you {@link #WATERED} and a perfectly
 * normal farm, so nobody is locked out. Only a running screw gets you {@link #BOOSTED}, which
 * is the sole tier where the Wonder's extinct crops will fruit -- so the puzzle is the only
 * route to the real reward, and dropping a bucket at the top of the terraces is not a shortcut.
 */
public enum BedTier implements StringRepresentable {
    DRY("dry"),
    WATERED("watered"),
    BOOSTED("boosted");

    private final String name;

    BedTier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
