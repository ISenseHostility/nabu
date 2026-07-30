package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags the mod reads at runtime, as opposed to ones only data refers to. */
public final class NabuTags {
    /**
     * Bare ground the shrine's greening turns to grass.
     *
     * <p>A tag rather than a {@link ai.jarno.nabu.block.DeadFoliage} implementation, because the
     * ground in question is vanilla's coarse dirt: an interface cannot be bolted onto a block we
     * do not own, and a mod-side lookalike would hand the player something that only pretends to
     * be the block it looks like. The tag keeps the roster in data, where the rest of the
     * Wonder's content lives, so widening it later is a one-line edit and no code at all.
     */
    public static final TagKey<Block> GREENS_INTO_GRASS =
            TagKey.create(Registries.BLOCK, Nabu.id("greens_into_grass"));

    private NabuTags() {
    }
}
