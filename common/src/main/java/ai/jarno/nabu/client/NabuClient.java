package ai.jarno.nabu.client;

import ai.jarno.nabu.client.render.WaterScrewModel;
import ai.jarno.nabu.client.render.WaterScrewRenderer;
import ai.jarno.nabu.registry.NabuBlockEntities;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;

/**
 * Client-only setup. Reached exclusively from the platform client entrypoints, so nothing on
 * a dedicated server can load these classes.
 */
public final class NabuClient {
    private NabuClient() {
    }

    public static void init() {
        EntityModelLayerRegistry.register(WaterScrewModel.LAYER, WaterScrewModel::create);
        BlockEntityRendererRegistry.register(NabuBlockEntities.WATER_SCREW.get(), WaterScrewRenderer::new);
    }
}
