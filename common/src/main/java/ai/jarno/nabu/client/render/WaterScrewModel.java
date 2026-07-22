package ai.jarno.nabu.client.render;

import ai.jarno.nabu.Nabu;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * PLACEHOLDER GEOMETRY. A central shaft with blades fanned around it, enough to read as a
 * turning auger so the mechanic can be tested. Intended to be replaced wholesale by a
 * hand-made model; only {@link #LAYER} and the part name "helix" need to survive that swap.
 */
public final class WaterScrewModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Nabu.id("water_screw"), "main");

    private static final int BLADES = 8;
    private static final float BLADE_SPACING = 1.75F;

    private WaterScrewModel() {
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition helix = root.addOrReplaceChild(
                "helix",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -8.0F, -1.0F, 2.0F, 16.0F, 2.0F),
                PartPose.ZERO);

        // Fan the blades a fixed step apart so the stack reads as a single continuous thread.
        for (int i = 0; i < BLADES; i++) {
            float yaw = (float) (i * (2.0 * Math.PI / BLADES));
            float y = -8.0F + 1.0F + i * BLADE_SPACING;
            helix.addOrReplaceChild(
                    "blade_" + i,
                    CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, -0.5F, -0.5F, 5.0F, 1.0F, 1.0F),
                    PartPose.offsetAndRotation(0.0F, y, 0.0F, 0.0F, yaw, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }
}
