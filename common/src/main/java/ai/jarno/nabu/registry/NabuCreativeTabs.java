package ai.jarno.nabu.registry;

import ai.jarno.nabu.Nabu;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class NabuCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Nabu.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeTabRegistry.create(
                    Component.translatable("itemGroup.nabu"),
                    () -> new ItemStack(NabuItems.FERTILITY_CHARM.get())));

    private NabuCreativeTabs() {
    }

    public static void register() {
        TABS.register();
    }
}
