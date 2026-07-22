package ai.jarno.nabu;

import ai.jarno.nabu.registry.NabuBlockEntities;
import ai.jarno.nabu.registry.NabuBlocks;
import ai.jarno.nabu.registry.NabuItems;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Common entry point. Everything here runs on both loaders; platform modules do nothing
 * but call {@link #init()} and host loader-specific hooks.
 */
public final class Nabu {
    public static final String MOD_ID = "nabu";

    private Nabu() {
    }

    public static void init() {
        // Blocks first: block items and block entity types both resolve blocks on registration.
        NabuBlocks.register();
        NabuItems.register();
        NabuBlockEntities.register();
    }

    /** Namespaced identifier in this mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Registry key for an object of this mod. 26.x requires objects to carry their own id
     * via {@code Properties.setId(...)}, so registration needs this alongside the name.
     */
    public static <T> ResourceKey<T> key(ResourceKey<Registry<T>> registry, String path) {
        return ResourceKey.create(registry, id(path));
    }
}
