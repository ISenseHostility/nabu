package ai.jarno.nabu.fabric;

import ai.jarno.nabu.client.NabuClient;
import net.fabricmc.api.ClientModInitializer;

public final class NabuFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NabuClient.init();
    }
}
