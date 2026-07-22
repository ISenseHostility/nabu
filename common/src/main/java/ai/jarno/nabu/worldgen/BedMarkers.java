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

    public static void handle(LevelAccessor level, StructureTemplate.StructureBlockInfo marker) {
        CompoundTag nbt = marker.nbt();
        if (nbt == null || !BED_MARKER.equals(nbt.getStringOr("metadata", ""))) {
            return;
        }

        BlockPos origin = marker.pos();
        int flagged = 0;

        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-MARKER_RADIUS_HORIZONTAL, -MARKER_RADIUS_VERTICAL, -MARKER_RADIUS_HORIZONTAL),
                origin.offset(MARKER_RADIUS_HORIZONTAL, MARKER_RADIUS_VERTICAL, MARKER_RADIUS_HORIZONTAL))) {
            if (!(level.getBlockState(candidate).getBlock() instanceof PlantingBedBlock)) {
                continue;
            }
            if (level.getBlockEntity(candidate) instanceof PlantingBedBlockEntity bed) {
                // One terrace per level, matching how the controller derives its indices.
                bed.flagAsWonderBed(candidate.getY());
                flagged++;
            }
        }

        Nabu.LOGGER.debug("Bed marker at {} flagged {} bed(s).", origin, flagged);
    }
}
