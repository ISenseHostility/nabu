package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;

/**
 * The Ancient Remedy, silphium's medicinal half.
 *
 * <p>Both variants carry the same {@code "ancient_remedy"} display name -- vanilla does the
 * same for its own long variants (LONG_REGENERATION is still named {@code "regeneration"}),
 * so a single set of lang keys covers the potion, its splash, lingering and tipped-arrow
 * forms, and the tooltip shows the duration difference.
 *
 * <p>Resistance I only, deliberately. Vanilla offers no brewing path to Resistance at all,
 * so there is no existing cost to anchor a II against, and an infinitely farmable 40% damage
 * reduction would be hard to walk back. The long variant covers the same ground safely.
 */
public final class NabuPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.POTION);

    private static final int THREE_MINUTES = 3 * 60 * 20;
    private static final int EIGHT_MINUTES = 8 * 60 * 20;

    public static final RegistrySupplier<Potion> ANCIENT_REMEDY = POTIONS.register(
            "ancient_remedy",
            () -> new Potion(
                    "ancient_remedy",
                    new MobEffectInstance(MobEffects.RESISTANCE, THREE_MINUTES)));

    public static final RegistrySupplier<Potion> LONG_ANCIENT_REMEDY = POTIONS.register(
            "long_ancient_remedy",
            () -> new Potion(
                    "ancient_remedy",
                    new MobEffectInstance(MobEffects.RESISTANCE, EIGHT_MINUTES)));

    private NabuPotions() {
    }

    public static void register() {
        POTIONS.register();
    }
}
