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
}
