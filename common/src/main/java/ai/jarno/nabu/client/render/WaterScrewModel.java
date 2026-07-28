package ai.jarno.nabu.client.render;

import ai.jarno.nabu.Nabu;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * An Archimedes screw: a central spindle wrapped in a continuous helical flight. The flight is
 * built from many short chord slabs, each pitched to the helix's climb angle and stepped a
 * fraction of its own thickness per segment, so the slabs overlap into one unbroken ribbon
 * rather than reading as separate stair treads.
 *
 * <p>Intended to be replaceable by a hand-made model; only {@link #LAYER} and the part name
 * "helix" need to survive that swap.
 *
 * <p>Texture regions on the 64x64 sheet: shaft box at (0,0) spanning u 0-12, v 0-19; flight
 * slab at (0,24) spanning u 0-13, v 24-27 (all segments share it).
 */
public final class WaterScrewModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Nabu.id("water_screw"), "main");

    /** Full revolutions the flight makes over the screw's one-block height. */
    private static final int TURNS = 2;

    private static final int SEGMENTS_PER_TURN = 24;
    private static final int SEGMENTS = TURNS * SEGMENTS_PER_TURN;

    private static final float SHAFT_HALF = 1.5F;

    /**
     * The flight slab starts buried inside the spindle rather than flush against its face --
     * a coplanar seam there would z-fight -- and reaches out to a 5.5px tip, inside the block
     * with room for the slab's half-width once rotated.
     */
    private static final float FLIGHT_INNER = 1.0F;

    private static final float FLIGHT_LENGTH = 4.5F;
    private static final float FLIGHT_THICKNESS = 1.0F;

    /**
     * Circumferential width of a slab. Successive segment centres at the 5.5px tip sit a chord
     * of {@code 2 * 5.5 * sin(180/24)} ~ 1.44px apart, so 2px of coverage closes the ribbon at
     * the rim with a comfortable overlap.
     */
    private static final float FLIGHT_WIDTH = 2.0F;

    /**
     * Segment centres stay this far inside the block so the tilted slab's corners do too. The
     * slimmer radius climbs more steeply, tilting each slab further and pushing its corners
     * higher, so the margin is wider than the 0.75px the fatter screw got away with.
     */
    private static final float FLIGHT_TOP = 7.15F;

    private static final float FLIGHT_BOTTOM = -7.15F;

    private WaterScrewModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition helix = root.addOrReplaceChild(
                "helix",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-SHAFT_HALF, -8.0F, -SHAFT_HALF,
                                SHAFT_HALF * 2.0F, 16.0F, SHAFT_HALF * 2.0F),
                PartPose.ZERO);

        float rise = (FLIGHT_TOP - FLIGHT_BOTTOM) / (SEGMENTS - 1);
        float pitch = rise * SEGMENTS_PER_TURN;
        float midRadius = (FLIGHT_INNER + FLIGHT_INNER + FLIGHT_LENGTH) / 2.0F;
        // How steeply the helix climbs at the slab's midpoint. Tilting each slab by exactly
        // this is what fuses the stack into a ribbon: the slab's raised edge meets the next
        // segment's lowered one instead of leaving a stair step between them.
        float tilt = (float) Math.atan(pitch / (2.0 * Math.PI * midRadius));
        float yawStep = (float) (2.0 * Math.PI / SEGMENTS_PER_TURN);

        for (int i = 0; i < SEGMENTS; i++) {
            float y = FLIGHT_BOTTOM + i * rise;
            // Yaw runs opposite to height. ModelPart applies xRot innermost, so the tilt is
            // about the slab's own radial axis, and its sign raises the +Z (yaw-behind) edge
            // to meet the segment above. Together these give the handedness that makes the
            // renderer's positive spin read as the thread travelling upward -- flip either
            // sign alone and the screw appears to push the water back down.
            helix.addOrReplaceChild(
                    "flight_" + i,
                    CubeListBuilder.create()
                            .texOffs(0, 24)
                            .addBox(FLIGHT_INNER, -FLIGHT_THICKNESS / 2.0F, -FLIGHT_WIDTH / 2.0F,
                                    FLIGHT_LENGTH, FLIGHT_THICKNESS, FLIGHT_WIDTH),
                    PartPose.offsetAndRotation(0.0F, y, 0.0F, -tilt, -i * yawStep, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }
}
