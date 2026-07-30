package ai.jarno.nabu.client.sound;

import ai.jarno.nabu.block.WaterScrewBlock;
import ai.jarno.nabu.registry.NabuSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The turning-water loop, anchored on one screw.
 *
 * <p>Self-terminating on purpose: rather than have something else watch for the screw stopping,
 * being broken, or its chunk streaming out, the instance re-reads the block every tick and stops
 * itself the moment that position is no longer a running screw. An unloaded chunk reads as air,
 * so streaming away is covered by the same check as everything else.
 */
public class WaterScrewSoundInstance extends AbstractTickableSoundInstance {
    private final Level level;
    private final BlockPos pos;

    public WaterScrewSoundInstance(Level level, BlockPos pos) {
        super(NabuSounds.WATER_SCREW_RUNNING.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos.immutable();

        this.looping = true;
        this.delay = 0;
        this.volume = 0.45F;
        this.pitch = 0.8F;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public void tick() {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WaterScrewBlock && state.getValue(WaterScrewBlock.RUNNING)) {
            return;
        }
        // Drop our own registration before stopping, so the position is immediately free to
        // start a fresh loop if the screw comes back.
        ClientScrewAudio.forget(this);
        stop();
    }
}
