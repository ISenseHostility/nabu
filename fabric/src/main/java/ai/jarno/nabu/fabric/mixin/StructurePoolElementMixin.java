package ai.jarno.nabu.fabric.mixin;

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
 * Jigsaw data markers have no event on either loader, so both mixin the same vanilla no-op.
 * NeoForge does not patch this class, so the two hooks are identical.
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
