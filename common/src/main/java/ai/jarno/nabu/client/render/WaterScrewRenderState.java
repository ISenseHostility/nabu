package ai.jarno.nabu.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * 26.2 renders block entities in two phases: extract, then submit. {@code submit} never sees
 * the block entity, and {@link BlockEntityRenderState#blockState} is private, so anything the
 * renderer needs has to be carried across explicitly here.
 */
public class WaterScrewRenderState extends BlockEntityRenderState {
    public boolean running;
    /** Absolute helix angle in degrees, already interpolated for the current frame. */
    public float spin;
    /**
     * Charge as a fraction, 0 dry to 1 primed. Drives the water's height and the spin speed
     * together, so the screw visibly winds up as the water climbs inside it.
     */
    public float fill;
    /** Upward texture offset in sprite heights. Only the fractional part is used. */
    public float scroll;
    /** Packed ARGB: biome water colour, so the column matches the water around it. */
    public int waterColor;
    /**
     * Water sits directly on this column's surface, so the surface is an internal seam and gets
     * dropped -- the same reason glass does not draw the face between two glass blocks.
     */
    public boolean capped;
    /**
     * How far the column reaches below its block to meet the water feeding it. Vanilla renders
     * a source under a non-water block short of full height, and the screw is not water.
     */
    public float skirt;
    /**
     * Bitmask of the column's four sides that face something other than water, and so are worth
     * drawing. A face shared with water is an internal seam, dropped for the same reason the
     * surface is when something wet rests on it.
     */
    public int openSides;
    /**
     * Same mask as {@link #openSides} but for the skirt, tested against the water beside the
     * block below rather than beside the screw. Falling water there covers the skirt and culls
     * it; a reservoir source warps down and leaves it open.
     */
    public int skirtSides;
    /**
     * Height the column is actually drawn to. Follows {@link #fill}, except that a brimming
     * column beside a pond drops to the pond's surface rather than standing proud of it.
     */
    public float surface;
}
