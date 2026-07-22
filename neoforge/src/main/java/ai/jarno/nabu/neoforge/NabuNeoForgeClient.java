package ai.jarno.nabu.neoforge;

import ai.jarno.nabu.Nabu;
import ai.jarno.nabu.client.NabuClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * Client-only companion to {@link NabuNeoForge}. Kept dist-gated so the renderer classes are
 * never loaded on a dedicated server.
 */
@Mod(value = Nabu.MOD_ID, dist = Dist.CLIENT)
public final class NabuNeoForgeClient {
    public NabuNeoForgeClient() {
        NabuClient.init();
    }
}
