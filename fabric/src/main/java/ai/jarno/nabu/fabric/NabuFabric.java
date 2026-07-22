package ai.jarno.nabu.fabric;

import ai.jarno.nabu.Nabu;
import net.fabricmc.api.ModInitializer;

public final class NabuFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Nabu.init();
    }
}
