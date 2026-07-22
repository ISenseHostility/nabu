package ai.jarno.nabu.neoforge.mixin;

import ai.jarno.nabu.worldgen.BedMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Twin of the Fabric mixin. Kept per-platform rather than shared so each loader keeps its own
 * mixin config; the logic itself lives in common and is identical for both.
 */
@Mixin(StructurePoolElement.class)
public abstract class StructurePoolElementMixin {
    @Inject(method = "handleDataMarker", at = @At("HEAD"))
    private void nabu$flagWonderBeds(
            LevelAccessor level,
            StructureTemplate.StructureBlockInfo dataMarker,
            BlockPos position,
            Rotation rotation,
            RandomSource random,
            BoundingBox chunkBB,
            CallbackInfo ci) {
        BedMarkers.handle(level, dataMarker, position);
    }
}
