package ai.jarno.nabu.fabric;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.brewing.NabuBrewing;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.FabricPotionBrewingBuilder;

public final class NabuFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Nabu.init();
        // Fabric rebuilds PotionBrewing whenever the enabled feature set changes, so the
        // mixes are contributed through this event rather than added once at startup.
        FabricPotionBrewingBuilder.BUILD.register(NabuBrewing::apply);
    }
}
