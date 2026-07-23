package ai.jarno.nabu.client.render;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.blockentity.WaterScrewBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterScrewRenderer implements BlockEntityRenderer<WaterScrewBlockEntity, WaterScrewRenderState> {
    private static final Identifier TEXTURE = Nabu.id("textures/block/water_screw.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

    /**
     * Block-atlas translucent geometry drawn outside the chunk mesh -- exactly what a moving
     * piston needs, and exactly what this is. Culls back faces, which keeps the column's far
     * side from blending through its near side.
     */
    private static final RenderType WATER_TYPE = RenderTypes.translucentMovingBlock();

    /**
     * Deliberately the deprecated {@code LOCATION_BLOCKS} and not {@code AtlasIds.BLOCKS}.
     * A {@link SpriteId}'s atlas component is looked up by the atlas's <em>texture</em> path
     * ({@code textures/atlas/blocks.png}); {@code AtlasIds} holds the datagen ids for the
     * {@code atlases/*.json} definitions instead, and passing one here throws at renderer
     * construction. Don't "fix" this deprecation without checking {@code AtlasManager.get}.
     */
    private static final SpriteId WATER_SPRITE =
            new SpriteId(TextureAtlas.LOCATION_BLOCKS, Identifier.withDefaultNamespace("block/water_still"));

    /** Degrees per tick at full charge. One full turn roughly every 2 seconds. */
    private static final float SPIN_SPEED = 9.0F;

    /** Sprite heights per tick. One full pass of the texture per second. */
    private static final float SCROLL_SPEED = 0.05F;

    private static final int WATER_ALPHA = 0xC0;

    /** Fill at which the column reaches the block boundary, with slack for float division. */
    private static final float FULL = 0.999F;

    /** Vanilla's fallback water tint, for the rare level that cannot resolve a biome colour. */
    private static final int DEFAULT_WATER_RGB = 0x3F76E4;

    /**
     * The clock is wrapped to one Minecraft day before reaching a float. Game time grows without
     * bound and a float loses sub-tick precision past about 16.7M ticks, which would make both
     * the scroll and the spin stutter on a long-lived world.
     */
    private static final long CLOCK_WRAP = 24000L;

    private final ModelPart helix;
    private final TextureAtlasSprite waterSprite;

    /**
     * Accumulated helix angle per screw. Spin speed scales with charge, so the angle is the
     * integral of a rate that changes -- it cannot be recovered from the clock alone and has to
     * be carried forward. Entries survive a screw stopping so it resumes from the angle it
     * halted at rather than snapping back to zero.
     */
    private final Map<BlockPos, Spin> spins = new HashMap<>();

    public WaterScrewRenderer(BlockEntityRendererProvider.Context context) {
        this.helix = context.bakeLayer(WaterScrewModel.LAYER);
        this.waterSprite = context.sprites().get(WATER_SPRITE);
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

        Level level = screw.getLevel();
        if (level == null) {
            state.fill = 0.0F;
            state.scroll = 0.0F;
            state.capped = false;
            state.skirt = 0.0F;
            state.waterColor = (WATER_ALPHA << 24) | DEFAULT_WATER_RGB;
            return;
        }

        // Charge, not RUNNING, drives the visuals: the screw has to be turning and filling all
        // through the prime, not stand dead still for two seconds and then snap into motion.
        state.fill = screw.charge(partialTicks) / WaterScrewBlockEntity.CHARGE_MAX;

        float time = (float) (level.getGameTime() % CLOCK_WRAP) + partialTicks;
        state.scroll = time * SCROLL_SPEED;
        state.waterColor = waterColor(level, screw.getBlockPos());
        state.spin = advanceSpin(screw.getBlockPos(), state.fill, time);
        // Only a brimming column reaches the block boundary; a partly filled one has its own
        // air above it and keeps its surface however wet the block overhead is.
        state.capped = state.fill >= FULL && waterAbove(level, screw.getBlockPos(), partialTicks);
        state.skirt = skirtBelow(level, screw.getBlockPos());
    }

    @Override
    public void submit(
            WaterScrewRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        // Water first, in raw block-local space and unrotated, so it stands still while the
        // auger turns inside it. Copied into locals because the render state is reused between
        // frames and the collector renders this lambda later.
        if (state.fill > 0.0F) {
            float fill = state.fill;
            float scroll = state.scroll;
            int color = state.waterColor;
            int light = state.lightCoords;
            boolean capped = state.capped;
            float skirt = state.skirt;
            collector.submitCustomGeometry(poseStack, WATER_TYPE, (pose, consumer) ->
                    WaterColumn.emit(pose, consumer, waterSprite, fill, scroll, color, light, capped, skirt));
        }

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

    /**
     * Whether water rests directly on this screw's ceiling: either the source a running screw
     * delivers, or the foot of another screw's column stacked on top. Any charge at all above
     * counts -- a column grows from its own floor upward, so even a shallow one is already
     * touching this surface.
     */
    private static boolean waterAbove(Level level, BlockPos pos, float partialTicks) {
        BlockPos above = pos.above();
        if (level.getFluidState(above).isSourceOfType(Fluids.WATER)) {
            return true;
        }
        return level.getBlockEntity(above) instanceof WaterScrewBlockEntity screw
                && screw.charge(partialTicks) > 0.0F;
    }

    /**
     * How far the water feeding this screw falls short of the block boundary.
     *
     * <p>Vanilla renders a source below full height unless the block above it holds the same
     * fluid, and a screw does not. Left alone that leaves a visible band of air between the
     * reservoir's surface and the foot of the column, so the column reaches down to meet it.
     * Measured rather than assumed to be 8/9, so flowing water lines up too.
     */
    private static float skirtBelow(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        FluidState fluid = level.getFluidState(below);
        if (!fluid.isSourceOfType(Fluids.WATER)) {
            return 0.0F;
        }
        return Math.max(0.0F, 1.0F - fluid.getHeight(level, below));
    }

    private static int waterColor(Level level, BlockPos pos) {
        int rgb = DEFAULT_WATER_RGB;
        if (level instanceof BlockAndTintGetter tinted) {
            rgb = BiomeColors.getAverageWaterColor(tinted, pos);
        }
        return (WATER_ALPHA << 24) | (rgb & 0x00FFFFFF);
    }

    private float advanceSpin(BlockPos pos, float fill, float time) {
        Spin spin = spins.computeIfAbsent(pos.immutable(), p -> new Spin());
        if (!Float.isNaN(spin.lastTime)) {
            float delta = time - spin.lastTime;
            // Ignore a clock that jumped: the day wrap above, a teleport, a dimension change.
            if (delta > 0.0F && delta < 100.0F) {
                spin.angle = (spin.angle + delta * SPIN_SPEED * fill) % 360.0F;
            }
        }
        spin.lastTime = time;
        return spin.angle;
    }

    private static final class Spin {
        private float angle;
        private float lastTime = Float.NaN;
    }
}
