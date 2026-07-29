package ai.jarno.nabu.item;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The seam between the tablet item and the book screen it opens.
 *
 * <p>Screens are client classes and common must never name one, so the item hands its pages to
 * this hook and the client entrypoint installs something that can actually show them. On a
 * dedicated server the handler stays the no-op below and no screen class is ever loaded.
 */
public final class TabletReader {
    /** Installed by the client entrypoint. Deliberately a no-op everywhere else. */
    private static Handler handler = pages -> {
    };

    private TabletReader() {
    }

    @FunctionalInterface
    public interface Handler {
        void open(List<Component> pages);
    }

    public static void install(Handler installed) {
        handler = installed;
    }

    public static void open(List<Component> pages) {
        handler.open(pages);
    }
}
