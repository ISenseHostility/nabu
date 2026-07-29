package ai.jarno.nabu.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The seam between the screw's common logic and its client-only running sound.
 *
 * <p>A looping sound instance is a client class, and common must never name one. So common owns
 * this hook and calls it from the screw's client ticker; the client entrypoint installs the real
 * implementation at startup. On a dedicated server nothing ever installs anything, the handler
 * stays the no-op below, and no client class is loaded.
 */
public final class ScrewAudio {
    /** Installed by the client entrypoint. Deliberately a no-op everywhere else. */
    private static Handler handler = (level, pos, state) -> {
    };

    private ScrewAudio() {
    }

    @FunctionalInterface
    public interface Handler {
        void tick(Level level, BlockPos pos, BlockState state);
    }

    public static void install(Handler installed) {
        handler = installed;
    }

    public static void tick(Level level, BlockPos pos, BlockState state) {
        handler.tick(level, pos, state);
    }
}
