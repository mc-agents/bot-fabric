package kr.junhyung.mcagents.botfabric.tools;

import com.mojang.blaze3d.platform.Window;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * The client's mouse, driven from where the window system would drive it.
 *
 * <p>Every event goes in through {@link MouseHandler}'s own callbacks, so what a screen does with it
 * -- which widget takes the press, which run of text the hit test lands on, what the press sends --
 * is what it does for a player. Calling a widget or sending its action directly would skip exactly
 * the part that goes wrong: a glyph drawn outside its widget, a piece covered by the next one.
 *
 * <p>The host's pointer is never moved. On a desktop that is the developer's own mouse, and the
 * handler only ever reads it again when a screen opens or the window is resized.
 *
 * <p>Positions are in GUI-scaled units, the ones a screen lays itself out in. The handler keeps
 * window pixels, so a position is put in the middle of the pixels that scale down to it.
 */
final class Mouse {

    /*
    The window system's numbering, which 26.x hands through unchanged: the left button is 1 and the
    right is 3. A widget takes a press only from button 1, so the 0 and 1 of the old numbering press
    nothing on a screen and say nothing about it.
    */
    static final int LEFT = 1;
    static final int RIGHT = 3;

    /** Thirty seconds: a resource pack being applied or taken away reloads every texture and font. */
    static final int OVERLAY_TICKS = 600;

    private static final int PRESS = 1;
    private static final int RELEASE = 0;

    private Mouse() {
    }

    /**
     * Whether the mouse goes nowhere right now. While the client loads -- a resource pack applied or
     * taken away -- its overlay is up, the handler drops every press, and a press made then would be
     * reported as made when the screen never heard it.
     */
    static boolean blocked() {
        return Mc.client().gui.overlay() != null;
    }

    static ToolException stillLoading() {
        return ToolException.refused("CLIENT_LOADING", "the client is still loading resources after "
                + OVERLAY_TICKS / 20 + " seconds, and takes no mouse input until it is done");
    }

    static void moveTo(int x, int y) {
        Window window = Mc.client().getWindow();
        MouseHandler handler = Mc.client().mouseHandler;
        double pixelX = (x + 0.5) * window.getScreenWidth() / window.getGuiScaledWidth();
        double pixelY = (y + 0.5) * window.getScreenHeight() / window.getGuiScaledHeight();

        handler.onMove(window.handle(), pixelX, pixelY, pixelX - handler.xpos(), pixelY - handler.ypos());
    }

    static void press(int button) {
        Mc.client().mouseHandler.onButton(Mc.client().getWindow().handle(), new MouseButtonInfo(button, 0), PRESS);
    }

    static void release(int button) {
        Mc.client().mouseHandler.onButton(Mc.client().getWindow().handle(), new MouseButtonInfo(button, 0), RELEASE);
    }

    /** Notches of the wheel: positive turns it up, which scrolls a list back towards its top. */
    static void scroll(int notches) {
        Mc.client().mouseHandler.onScroll(Mc.client().getWindow().handle(), 0, notches);
    }
}
