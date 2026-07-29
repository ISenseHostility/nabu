package ai.jarno.nabu.client.sound;

import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.blockentity.ScrewAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps at most one running loop per screw.
 *
 * <p>Driven from the screw's client ticker rather than its renderer: block-entity renderers are
 * skipped for culled chunk sections, so a renderer-driven loop would cut out whenever the player
 * stepped behind a wall and start again on the way back.
 */
public final class ClientScrewAudio implements ScrewAudio.Handler {
    /**
     * Live loops by position. Entries remove themselves -- see
     * {@link WaterScrewSoundInstance#tick()} -- so nothing here needs sweeping. The stale-entry
     * guard below covers the one case that skips that path: leaving a level drops every sound
     * without ticking it.
     */
    private static final Map<BlockPos, WaterScrewSoundInstance> ACTIVE = new HashMap<>();

    public static void install() {
        ScrewAudio.install(new ClientScrewAudio());
    }

    static void forget(WaterScrewSoundInstance instance) {
        ACTIVE.remove(instance.pos(), instance);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(WaterScrewBlock.RUNNING)) {
            return;
        }
        WaterScrewSoundInstance existing = ACTIVE.get(pos);
        if (existing != null && !existing.isStopped()) {
            return;
        }

        WaterScrewSoundInstance instance = new WaterScrewSoundInstance(level, pos);
        ACTIVE.put(instance.pos(), instance);
        Minecraft.getInstance().getSoundManager().play(instance);
    }
}
