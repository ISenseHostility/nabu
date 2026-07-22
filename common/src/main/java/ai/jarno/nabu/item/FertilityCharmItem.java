package ai.jarno.nabu.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The charm itself carries no behaviour -- {@link FertilityCharm} does the work, keyed off the
 * item's identity. This subclass exists only so the offhand requirement is discoverable, since
 * nothing else in the game would tell the player.
 */
public class FertilityCharmItem extends Item {
    public FertilityCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack itemStack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable("item.nabu.fertility_charm.desc")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.nabu.fertility_charm.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
