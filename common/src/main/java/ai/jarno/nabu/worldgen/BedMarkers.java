package ai.jarno.nabu.worldgen;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.block.PlantingBedBlock;
import ai.jarno.nabu.blockentity.PlantingBedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Turns worldgen data markers into flagged Wonder beds.
 *
 * <p>Neither loader exposes an event for jigsaw data markers, so both platforms mixin into
 * vanilla's {@code StructurePoolElement#handleDataMarker} -- a public, empty-bodied method that
 * exists precisely as an extension point -- and funnel into here.
 *
 * <p>This only <em>flags</em> beds. It deliberately does not try to find the controller: jigsaw
 * pieces are placed chunk by chunk, so the shrine frequently does not exist yet when a bed
 * piece lands. The controller adopts flagged beds itself once it is ticking, which sidesteps
 * the ordering problem entirely.
 */
public final class BedMarkers {
    /** Metadata string to put on the data marker in a bed piece. */
    public static final String BED_MARKER = "nabu:beds";

    /** How far from the marker to look for beds, so one marker can cover a whole plot. */
    private static final int MARKER_RADIUS_HORIZONTAL = 4;
    private static final int MARKER_RADIUS_VERTICAL = 2;

    private BedMarkers() {
    }

    /**
     * @param piecePosition world position of the piece being placed, used only as a fallback
     *                      origin -- see {@link #flagAround}
     */
    public static void handle(LevelAccessor level, StructureTemplate.StructureBlockInfo marker, BlockPos piecePosition) {
        CompoundTag nbt = marker.nbt();
        if (nbt == null || !BED_MARKER.equals(nbt.getStringOr("metadata", ""))) {
            return;
        }

        BlockPos markerPos = marker.pos();

        // Vanilla's own marker handlers read this position as world-absolute, but that is not
        // something we can prove without running worldgen. If the absolute reading finds
        // nothing, fall back to treating it as piece-relative. Whichever hits is logged, so the
        // ambiguity resolves itself the first time a Wonder generates.
        int flagged = flagAround(level, markerPos);
        if (flagged > 0) {
            Nabu.LOGGER.info("Bed marker at {} flagged {} bed(s) [absolute].", markerPos, flagged);
            return;
        }

        BlockPos relative = piecePosition.offset(markerPos);
        flagged = flagAround(level, relative);
        if (flagged > 0) {
            Nabu.LOGGER.info("Bed marker at {} flagged {} bed(s) [piece-relative].", relative, flagged);
            return;
        }

        Nabu.LOGGER.warn(
                "Bed marker found no planting beds near {} or {}. Is the marker within {} blocks "
                        + "horizontally and {} vertically of the beds?",
                markerPos, relative, MARKER_RADIUS_HORIZONTAL, MARKER_RADIUS_VERTICAL);
    }

    private static int flagAround(LevelAccessor level, BlockPos origin) {
        int flagged = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-MARKER_RADIUS_HORIZONTAL, -MARKER_RADIUS_VERTICAL, -MARKER_RADIUS_HORIZONTAL),
                origin.offset(MARKER_RADIUS_HORIZONTAL, MARKER_RADIUS_VERTICAL, MARKER_RADIUS_HORIZONTAL))) {
            if (!(level.getBlockState(candidate).getBlock() instanceof PlantingBedBlock)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof PlantingBedBlockEntity bed) {
                // One terrace per level, matching how the shrine derives its indices.
                bed.flagAsWonderBed(candidate.getY());
                flagged++;
            }
        }
        return flagged;
    }
}
