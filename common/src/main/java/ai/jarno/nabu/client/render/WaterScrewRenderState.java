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
}
