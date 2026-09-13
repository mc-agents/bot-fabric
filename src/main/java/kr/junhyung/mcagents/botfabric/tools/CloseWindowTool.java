package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;

/**
 * Close whatever window is open, so the next thing that opens one is not refused.
 *
 * <p>Nothing being open is a state and not a mistake: asking to close nothing is a no-op. Each
 * kind of bot used to refuse it with its own wording, which is how the two came to describe one
 * state two ways.
 *
 * <p>Whatever is open, and not only a container. This answered "no window was open" in front of a
 * book, a sign editor or a dialog, and left it there, because it only ever looked for a chest. It
 * now does what Escape does, which is also where it stops: a screen that does not close on Escape
 * -- the death screen -- is refused rather than dismissed into a state no player could reach.
 */
public final class CloseWindowTool extends ReadTool {

    public CloseWindowTool() {
        super("close-window");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        JsonObject data = new JsonObject();
        Screen screen = Mc.screen();

        if (screen == null) {
            data.add("closed", JsonNull.INSTANCE);
            data.add("screen", JsonNull.INSTANCE);
            return data;
        }

        data.addProperty("closed", screen.getTitle().getString());
        data.add("closedComponent", Segments.raw(screen.getTitle()));

        if (screen instanceof AbstractContainerScreen<?>) {
            data.add("screen", JsonNull.INSTANCE);
            Mc.requirePlayer().closeContainer();
            return data;
        }

        if (!screen.shouldCloseOnEsc()) {
            throw ToolException.refused("SCREEN_STAYS_OPEN", "the " + name(screen) + " does not close on"
                    + " Escape, so it is not closed from here either"
                    + (screen instanceof DeathScreen ? ". Call respawn to leave it." : "."));
        }

        data.addProperty("screen", name(screen));
        screen.onClose();

        return data;
    }

    /** What a player would call the thing, since the class name is not something they ever see. */
    private static String name(Screen screen) {
        return switch (screen) {
            case LecternScreen ignored -> "lectern";
            case BookViewScreen ignored -> "book";
            case BookEditScreen ignored -> "book editor";
            case AbstractSignEditScreen ignored -> "sign editor";
            case DialogScreen<?> ignored -> "dialog";
            case AbstractCommandBlockEditScreen ignored -> "command block editor";
            case WinScreen ignored -> "end credits";
            case DeathScreen ignored -> "death screen";
            default -> screen.getClass().getSimpleName();
        };
    }
}
