package ai.jarno.nabu.client;

import ai.jarno.nabu.item.TabletReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shows the tablet on vanilla's book screen. {@code BookAccess} takes a plain list of
 * components, so this needs no written book item and no data component of its own.
 */
public final class ClientTabletReader implements TabletReader.Handler {
    private ClientTabletReader() {
    }

    public static void install() {
        TabletReader.install(new ClientTabletReader());
    }

    @Override
    public void open(List<Component> pages) {
        // 26.2 renamed Minecraft.setScreen to setScreenAndShow.
        Minecraft.getInstance().setScreenAndShow(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }
}
