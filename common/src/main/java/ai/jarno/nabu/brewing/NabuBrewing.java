package ai.jarno.nabu.brewing;

import ai.jarno.nabu.registry.NabuItems;
import ai.jarno.nabu.registry.NabuPotions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Every brewing mix this mod adds, in one place.
 *
 * <p>Brewing is the one recipe kind with no datapack representation: {@code PotionBrewing} is
 * assembled in code at bootstrap and each loader exposes its own hook into that assembly.
 * The mixes themselves are shared logic, so they are declared here and the platform modules
 * only hand this method their loader's builder. Neither of them makes a decision, which is
 * what keeps the two from drifting apart.
 */
public final class NabuBrewing {
    private NabuBrewing() {
    }

    /** Adds this mod's mixes to a builder supplied by whichever loader is running. */
    public static void apply(PotionBrewing.Builder builder) {
        builder.addMix(
                Potions.AWKWARD,
                NabuItems.SILPHIUM_RESIN.get(),
                NabuPotions.ANCIENT_REMEDY.asHolder());
        builder.addMix(
                NabuPotions.ANCIENT_REMEDY.asHolder(),
                Items.REDSTONE,
                NabuPotions.LONG_ANCIENT_REMEDY.asHolder());
    }
}
