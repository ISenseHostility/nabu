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

    /** FluidRenderer's corner weighting: samples this deep or deeper count ten times over. */
    private static final float HEAVY_ABOVE = 0.8F;

    private static final float HEAVY_WEIGHT = 10.0F;

    /** Slack when asking whether a neighbour's water covers a face, against float drift. */
    private static final float COVER_SLACK = 0.001F;

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
            state.openSides = WaterColumn.ALL_SIDES;
            state.skirtSides = WaterColumn.ALL_SIDES;
            state.surface = 0.0F;
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
        // Surface first: how tall the column ends up drawn decides which sides are covered.
        state.surface = surfaceHeight(level, screw.getBlockPos(), state.fill);
        state.capped =
                state.surface >= FULL && hasWater(level, screw.getBlockPos().above(), partialTicks);
        state.skirt = skirtBelow(level, screw.getBlockPos());
        state.openSides =
                openSides(level, screw.getBlockPos(), state.surface, partialTicks);
        state.skirtSides = skirtSides(level, screw.getBlockPos());
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
            float fill = state.surface;
            float scroll = state.scroll;
            int color = state.waterColor;
            int light = state.lightCoords;
            boolean capped = state.capped;
            float skirt = state.skirt;
            int openSides = state.openSides;
            int skirtSides = state.skirtSides;
            collector.submitCustomGeometry(poseStack, WATER_TYPE, (pose, consumer) ->
                    WaterColumn.emit(
                            pose, consumer, waterSprite, fill, scroll, color, light,
                            capped, skirt, openSides, skirtSides));
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
     * Whether the block holds water at all -- source or flowing -- or another screw carrying a
     * column of its own. Any charge counts: a column grows from its own floor upward, so even a
     * shallow one already meets the neighbour it shares a face with.
     */
    private static boolean hasWater(Level level, BlockPos pos, float partialTicks) {
        if (level.getFluidState(pos).getType().isSame(Fluids.WATER)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof WaterScrewBlockEntity screw
                && screw.charge(partialTicks) > 0.0F;
    }

    /**
     * Whether the block's water stands tall enough to cover a face reaching {@code surface}.
     *
     * <p>Deliberately matched against flowing water and not just sources. A screw set in a fall
     * is ringed by flowing blocks, and a source-only test leaves every side drawn -- which is
     * what makes the column read as a box hanging inside the water rather than part of it.
     *
     * <p>Height still has to be checked, so a shallow trickle alongside cannot cull a face it
     * does not actually cover. Water falling in a column is not that case: it reports a full
     * block, because the fluid above it is the same fluid.
     */
    private static boolean waterCovers(
            Level level, BlockPos pos, float surface, float partialTicks) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.getType().isSame(Fluids.WATER)) {
            return fluid.getHeight(level, pos) >= surface - COVER_SLACK;
        }
        return level.getBlockEntity(pos) instanceof WaterScrewBlockEntity screw
                && screw.charge(partialTicks) / WaterScrewBlockEntity.CHARGE_MAX
                        >= surface - COVER_SLACK;
    }

    /**
     * Which of the column's four sides face something that does not cover them, and so are worth
     * drawing. A face shared with water is an internal seam: leaving it in stacks two translucent
     * surfaces back to back and outlines the column against the water it stands in, for the same
     * reason glass omits the face between two panes.
     */
    private static int openSides(Level level, BlockPos pos, float surface, float partialTicks) {
        int sides = 0;
        if (!waterCovers(level, pos.north(), surface, partialTicks)) {
            sides |= WaterColumn.NORTH;
        }
        if (!waterCovers(level, pos.south(), surface, partialTicks)) {
            sides |= WaterColumn.SOUTH;
        }
        if (!waterCovers(level, pos.west(), surface, partialTicks)) {
            sides |= WaterColumn.WEST;
        }
        if (!waterCovers(level, pos.east(), surface, partialTicks)) {
            sides |= WaterColumn.EAST;
        }
        return sides;
    }

    /**
     * Which of the skirt's four sides face something other than full-height water, and so are
     * worth drawing. The skirt hangs in the block below the screw, so it is the water beside
     * <em>that</em> block that matters, not beside the screw.
     *
     * <p>The distinction from {@link #openSides} is what tells the two placements apart. Falling
     * water is pinned to a full block by the water above it, so it covers the skirt and the side
     * is dropped. A reservoir source with the screw above warps its surface down, leaving the
     * gap the skirt bridges open at the sides, so that side is kept -- exactly the case the
     * skirt was added for.
     */
    private static int skirtSides(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        int sides = 0;
        if (!fullWater(level, below.north())) {
            sides |= WaterColumn.NORTH;
        }
        if (!fullWater(level, below.south())) {
            sides |= WaterColumn.SOUTH;
        }
        if (!fullWater(level, below.west())) {
            sides |= WaterColumn.WEST;
        }
        if (!fullWater(level, below.east())) {
            sides |= WaterColumn.EAST;
        }
        return sides;
    }

    /** Whether the block holds water filling it to the top -- a fall, not a warped-down pool. */
    private static boolean fullWater(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.getType().isSame(Fluids.WATER) && fluid.getHeight(level, pos) >= 1.0F - COVER_SLACK;
    }

    /**
     * How far the water feeding this screw falls short of the block boundary.
     *
     * <p>Vanilla warps a fluid's top face rather than laying it flat: every corner is a weighted
     * average of the block and the three around that corner, where open air counts as zero and
     * drags the corner down, solid blocks are dropped from the average rather than counted as
     * empty, and anything already brimming pins the corner to a full block.
     *
     * <p>That makes the shortfall depend entirely on what surrounds the water. A source in a
     * reservoir is ringed by more water and keeps almost all of its nominal 8/9, but the lone
     * source standing in the gap of a spaced column is ringed by air and sags to roughly 0.68 --
     * about three times the reach. No single constant serves both, so this measures it. Taking
     * the lowest of the four corners means no corner is left showing daylight.
     */
    private static float skirtBelow(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getFluidState(below).getType().isSame(Fluids.WATER)) {
            return 0.0F;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        float lowest = 1.0F;
        for (int corner = 0; corner < 4; corner++) {
            int dx = (corner & 1) == 0 ? -1 : 1;
            int dz = (corner & 2) == 0 ? -1 : 1;
            lowest = Math.min(lowest, cornerHeight(level, below, dx, dz, cursor));
        }
        return Math.max(0.0F, 1.0F - lowest);
    }

    /**
     * Height to draw the column's surface at.
     *
     * <p>A brimming column normally fills its block outright -- it is a contained volume, not a
     * free fluid. But a pond alongside draws its own surface below the block boundary, so that
     * flat lid stands proud of the water the screw is standing in and reads as a plate laid
     * over it. Where there is water beside the screw, drop the surface to meet the lowest of it.
     */
    private static float surfaceHeight(Level level, BlockPos pos, float fill) {
        if (fill < FULL) {
            // A partly filled column has its own surface with air above it; nothing to match.
            return fill;
        }
        return Math.min(
                Math.min(neighbourSurface(level, pos.north()), neighbourSurface(level, pos.south())),
                Math.min(neighbourSurface(level, pos.west()), neighbourSurface(level, pos.east())));
    }

    /**
     * Surface height of standing water beside the screw, or a full block where there is none.
     *
     * <p>Sources only, unlike the culling test. This drags the column's lid down to meet its
     * neighbour, so obeying a shallow trickle running past would collapse a full column to the
     * depth of the trickle. Water falling alongside needs no such match anyway: it reports a
     * full block, exactly where the lid already sits.
     */
    private static float neighbourSurface(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.isSourceOfType(Fluids.WATER) ? fluid.getHeight(level, pos) : 1.0F;
    }

    /** One corner of a fluid's top face, following FluidRenderer's weighting. */
    private static float cornerHeight(
            Level level, BlockPos pos, int dx, int dz, BlockPos.MutableBlockPos cursor) {
        float sum = 0.0F;
        float weight = 0.0F;
        for (int sample = 0; sample < 4; sample++) {
            int ox = (sample & 1) == 0 ? 0 : dx;
            int oz = (sample & 2) == 0 ? 0 : dz;
            float height =
                    sampleHeight(level, cursor.set(pos.getX() + ox, pos.getY(), pos.getZ() + oz));
            if (height >= 1.0F) {
                return 1.0F;
            }
            if (height >= 0.0F) {
                float w = height >= HEAVY_ABOVE ? HEAVY_WEIGHT : 1.0F;
                sum += height * w;
                weight += w;
            }
        }
        return weight <= 0.0F ? 0.0F : sum / weight;
    }

    /**
     * A single height sample: a full block where the fluid carries on upward, its own height
     * where it does not, zero for anything open, and a negative for solids -- which vanilla
     * leaves out of the average entirely rather than treating as empty.
     */
    private static float sampleHeight(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.getType().isSame(Fluids.WATER)) {
            return level.getFluidState(pos.above()).getType().isSame(Fluids.WATER)
                    ? 1.0F
                    : fluid.getOwnHeight();
        }
        return level.getBlockState(pos).isSolid() ? -1.0F : 0.0F;
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
