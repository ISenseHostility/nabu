package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

/**
 * The Wonder's own sound events.
 *
 * <p>Every one of these is currently backed by a vanilla {@code .ogg} aliased in
 * {@code assets/nabu/sounds.json}, so the mod is audible without shipping audio. The events
 * exist under this mod's namespace regardless, which is the whole point: dropping real
 * {@code .ogg} files into {@code assets/nabu/sounds/} and repointing that JSON replaces the
 * audio without touching a line of Java.
 */
public final class NabuSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.SOUND_EVENT);

    /** Looped for as long as the screw turns. Started and stopped client-side. */
    public static final RegistrySupplier<SoundEvent> WATER_SCREW_RUNNING =
            event("block.water_screw.running");

    public static final RegistrySupplier<SoundEvent> WATER_SCREW_START =
            event("block.water_screw.start");

    public static final RegistrySupplier<SoundEvent> WATER_SCREW_STOP =
            event("block.water_screw.stop");

    /** The soft chime a bed gives when it reaches Boosted. */
    public static final RegistrySupplier<SoundEvent> PLANTING_BED_BLOOM =
            event("block.planting_bed.bloom");

    public static final RegistrySupplier<SoundEvent> TERRACE_RESTORED =
            event("block.garden_shrine.terrace_restored");

    /** Body of the awakening swell. */
    public static final RegistrySupplier<SoundEvent> SHRINE_AWAKEN =
            event("block.garden_shrine.awaken");

    /** Sparkle layered over {@link #SHRINE_AWAKEN}, played twice at rising pitch. */
    public static final RegistrySupplier<SoundEvent> SHRINE_AWAKEN_CHIME =
            event("block.garden_shrine.awaken_chime");

    private NabuSounds() {
    }

    private static RegistrySupplier<SoundEvent> event(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Nabu.id(name)));
    }

    public static void register() {
        SOUND_EVENTS.register();
    }
}
