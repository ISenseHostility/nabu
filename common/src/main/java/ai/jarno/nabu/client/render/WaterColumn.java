package ai.jarno.nabu.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The water standing inside a running screw, drawn as loose quads rather than a baked
 * {@link net.minecraft.client.model.geom.ModelPart} because both the surface height and the
 * texture offset move every frame -- a mesh would have to be rebuilt on each one.
 *
 * <p>The climb is sold by sliding the V coordinate down the sprite over time, which walks the
 * pattern upward. V is kept inside the sprite's own slice of the block atlas at all times:
 * running past {@link TextureAtlasSprite#getV1()} would sample whichever sprite happens to be
 * stitched underneath it. When the scroll straddles the sprite's bottom edge the column is
 * split into two bands meeting at the seam, so the wrap never shows.
 */
public final class WaterColumn {
    /**
     * Holds the column just inside the block. Sitting exactly on the boundary z-fights with the
     * neighbour's face, and screws stacked flush would fight each other at every seam.
     */
    private static final float INSET = 0.002F;

    /**
     * Ceiling on the band walk. A full block plus its skirt spans at most two sprite seams;
     * this only exists so a degenerate scroll value can never spin the loop.
     */
    private static final int MAX_BANDS = 4;

    private WaterColumn() {
    }

    /**
     * @param height surface height in block units, 0 to 1
     * @param scroll upward texture offset in sprite heights; only the fractional part is used
     * @param color  packed ARGB, the biome water colour at the alpha to draw at
     * @param capped water rests on this column's surface, so the surface is dropped as an
     *               internal seam -- without it a stacked screw shows the column below's
     *               surface floating through its own water
     * @param skirt  how far to reach below the block. Vanilla draws a water source under a
     *               non-water block short of full height, and without bridging that the column
     *               visibly hangs above the reservoir feeding it
     */
    public static void emit(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            float height,
            float scroll,
            int color,
            int light,
            boolean capped,
            float skirt) {
        if (height <= 0.0F) {
            return;
        }

        float bottom = -skirt;
        // v grows downward through the sprite, so advancing it with the clock walks the pattern
        // up the column.
        float v = scroll - (float) Math.floor(scroll);

        // Walk down from the surface a band at a time, restarting at the top of the sprite each
        // time v runs off its bottom edge. A loop rather than a one-seam special case: the skirt
        // can push the run past a whole sprite height, so there may be more than one seam.
        float y = height;
        for (int bandIndex = 0; y > bottom && bandIndex < MAX_BANDS; bandIndex++) {
            if (v >= 1.0F) {
                v -= 1.0F;
            }
            float step = Math.min(1.0F - v, y - bottom);
            if (step <= 0.0F) {
                break;
            }
            band(pose, consumer, sprite, y - step, y, v + step, v, color, light);
            y -= step;
            v += step;
        }

        if (!capped) {
            surface(pose, consumer, sprite, height, color, light);
        }
    }

    /** One vertical slice of the column: four sides spanning a continuous run of the sprite. */
    private static void band(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            float yBottom,
            float yTop,
            float vBottomNorm,
            float vTopNorm,
            int color,
            int light) {
        if (yTop <= yBottom) {
            return;
        }

        float a = INSET;
        float b = 1.0F - INSET;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float vB = v(sprite, vBottomNorm);
        float vT = v(sprite, vTopNorm);

        // Wound counter-clockwise as seen from outside the block, so back faces cull away and
        // the translucent sides never blend over each other.
        quad(pose, consumer, color, light, 0.0F, 0.0F, -1.0F,
                b, yBottom, a, u0, vB,
                a, yBottom, a, u1, vB,
                a, yTop, a, u1, vT,
                b, yTop, a, u0, vT);
        quad(pose, consumer, color, light, 0.0F, 0.0F, 1.0F,
                a, yBottom, b, u0, vB,
                b, yBottom, b, u1, vB,
                b, yTop, b, u1, vT,
                a, yTop, b, u0, vT);
        quad(pose, consumer, color, light, -1.0F, 0.0F, 0.0F,
                a, yBottom, a, u0, vB,
                a, yBottom, b, u1, vB,
                a, yTop, b, u1, vT,
                a, yTop, a, u0, vT);
        quad(pose, consumer, color, light, 1.0F, 0.0F, 0.0F,
                b, yBottom, b, u0, vB,
                b, yBottom, a, u1, vB,
                b, yTop, a, u1, vT,
                b, yTop, b, u0, vT);
    }

    /**
     * The water's top face. Left unscrolled: it reads as a surface rather than a wall, and the
     * sprite's own frame animation already gives it movement.
     */
    private static void surface(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            float y,
            int color,
            int light) {
        float a = INSET;
        float b = 1.0F - INSET;
        quad(pose, consumer, color, light, 0.0F, 1.0F, 0.0F,
                a, y, a, sprite.getU0(), sprite.getV0(),
                a, y, b, sprite.getU0(), sprite.getV1(),
                b, y, b, sprite.getU1(), sprite.getV1(),
                b, y, a, sprite.getU1(), sprite.getV0());
    }

    private static float v(TextureAtlasSprite sprite, float t) {
        return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * t;
    }

    private static void quad(
            PoseStack.Pose pose, VertexConsumer consumer, int color, int light,
            float nx, float ny, float nz,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3) {
        vertex(pose, consumer, color, light, nx, ny, nz, x0, y0, z0, u0, v0);
        vertex(pose, consumer, color, light, nx, ny, nz, x1, y1, z1, u1, v1);
        vertex(pose, consumer, color, light, nx, ny, nz, x2, y2, z2, u2, v2);
        vertex(pose, consumer, color, light, nx, ny, nz, x3, y3, z3, u3, v3);
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer consumer, int color, int light,
            float nx, float ny, float nz,
            float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
