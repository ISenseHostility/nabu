package ai.jarno.nabu.client.render;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.blockentity.WaterScrewBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterScrewRenderer implements BlockEntityRenderer<WaterScrewBlockEntity, WaterScrewRenderState> {
    private static final Identifier TEXTURE = Nabu.id("textures/block/water_screw.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

    /** Degrees per tick. One full turn roughly every 2 seconds. */
    private static final float SPIN_SPEED = 9.0F;

    private final ModelPart helix;

    public WaterScrewRenderer(BlockEntityRendererProvider.Context context) {
        this.helix = context.bakeLayer(WaterScrewModel.LAYER);
    }

    @Override
    public WaterScrewRenderState createRenderState() {
        return new WaterScrewRenderState();
    }

    @Override
    public void extractRenderState(
            WaterScrewBlockEntity screw,
            WaterScrewRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(screw, state, breakProgress);

        state.running = screw.getBlockState().getValue(WaterScrewBlock.RUNNING);

        if (state.running && screw.getLevel() != null) {
            // Drive off world time so every screw in a chain turns in lockstep.
            float ticks = screw.getLevel().getGameTime() + partialTicks;
            state.spin = (ticks * SPIN_SPEED) % 360.0F;
        } else {
            state.spin = 0.0F;
        }
    }

    @Override
    public void submit(
            WaterScrewRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        poseStack.pushPose();
        // ModelPart already divides its vertices by 16 on the way out, so the mesh's pixel
        // dimensions arrive here as block units and the 16-pixel shaft is exactly one block
        // tall. Scaling by 1/16 again here is what shrank it to a single pixel. All this
        // needs is the recentre onto the middle of the block, which the mesh is built around.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));

        collector.submitModelPart(
                helix, poseStack, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        poseStack.popPose();
    }
}
