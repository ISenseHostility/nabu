package ai.jarno.nabu.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The scribe's account of the Gardens, and the only thing in the mod that tells a player what
 * the ruin is for.
 *
 * <p>Pages are {@code translatable} rather than baked onto the stack as a written book
 * component. That keeps the text translatable, keeps every tablet in the world identical, and
 * means correcting a line is a lang edit rather than something existing worlds carry forever.
 */
public class ClayTabletItem extends Item {
    private static final int PAGE_COUNT = 4;

    public ClayTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Reading is purely presentational -- there is no server-side effect to mirror.
        if (level.isClientSide()) {
            TabletReader.open(pages());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack itemStack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag) {
        builder.accept(Component.translatable("item.nabu.clay_tablet.desc")
                .withStyle(ChatFormatting.GRAY));
    }

    private static List<Component> pages() {
        List<Component> pages = new ArrayList<>(PAGE_COUNT);
        for (int page = 1; page <= PAGE_COUNT; page++) {
            pages.add(Component.translatable("item.nabu.clay_tablet.page" + page));
        }
        return pages;
    }
}
