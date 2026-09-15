package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A click answered with what the server made of it, not with what the client predicted.
 *
 * <p>The client applies a click to its own copy of the window as it sends it. A plugin that cancels
 * the click puts the slots back a moment later, and a click answered straight away reported a stack
 * moved into the cursor that was back in its slot by the next read -- which reads as an item copied
 * or lost. So the menu's state id is moved on before the click: the server, seeing an id it did not
 * send, applies the click and then sends the whole window, and the answer is read once that has
 * arrived. The other kind of bot has always answered this way.
 *
 * <p>A plugin's menu often answers a click with another window instead: a shop item opens the buy
 * screen, and a +1 opens the same screen again with the count moved on. The server then sends the new
 * window, under a new id, and never the one that was clicked. That is an answer too, and it goes back
 * as the window now open, with nothing said about slots of a window that is gone. A window the server
 * closes is answered the same way.
 *
 * <p>Not the creative inventory. Its clicks are not container clicks and the server sends nothing
 * back for them, so it is answered as the client has it, which in creative is what the server takes.
 */
public final class ServerResync {

    private static final long WAIT_MS = 2_000;

    /** Client thread only. */
    private static final List<Waiting> WAITING = new ArrayList<>();

    /** {@code answer} is given the window the click left open, or JSON null when it is the one clicked. */
    private record Waiting(int containerId, Function<JsonElement, JsonObject> answer, CallContext call, String tool) {

        void answer(JsonElement window) {
            call.ok(tool, answer.apply(window));
        }
    }

    private ServerResync() {
    }

    /**
     * Called on the client thread once the whole of a window has been applied. The window clicked
     * answers its own click; any other window that is now the open one answers a click on the window
     * it replaced.
     */
    public static void arrived(int containerId) {
        AbstractContainerMenu open = Mc.client().player == null ? null : Mc.client().player.containerMenu;
        for (Waiting waiting : List.copyOf(WAITING)) {
            if (waiting.containerId() == containerId) {
                WAITING.remove(waiting);
                waiting.answer(JsonNull.INSTANCE);
            } else if (open != null && open.containerId == containerId && Windows.open() != null) {
                WAITING.remove(waiting);
                waiting.answer(replacedBy(Windows.open()));
            }
        }
    }

    /** Called on the client thread when the server closes a window. */
    public static void closed(int containerId) {
        for (Waiting waiting : List.copyOf(WAITING)) {
            if (waiting.containerId() == containerId) {
                WAITING.remove(waiting);
                JsonObject window = new JsonObject();
                window.addProperty("closed", true);
                window.add("title", JsonNull.INSTANCE);
                window.add("titleComponent", JsonNull.INSTANCE);
                waiting.answer(window);
            }
        }
    }

    private static JsonObject replacedBy(AbstractContainerScreen<?> screen) {
        JsonObject window = new JsonObject();
        window.addProperty("closed", false);
        window.addProperty("title", screen.getTitle().getString());
        window.add("titleComponent", Segments.raw(screen.getTitle()));
        return window;
    }

    /**
     * Clicks, then answers with the DTO read after the server's copy of the window is in, given the
     * window that replaced the one clicked or JSON null. Client thread only.
     */
    static void click(CallContext call, String tool, AbstractContainerScreen<?> screen, Runnable click,
            Function<JsonElement, JsonObject> answer) {
        if (screen instanceof CreativeModeInventoryScreen) {
            click.run();
            call.ok(tool, answer.apply(JsonNull.INSTANCE));
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        Waiting waiting = new Waiting(menu.containerId, answer, call, tool);
        WAITING.add(waiting);
        menu.incrementStateId();
        click.run();

        CompletableFuture.delayedExecutor(WAIT_MS, TimeUnit.MILLISECONDS).execute(() -> Mc.client().execute(() -> {
            if (WAITING.remove(waiting)) {
                call.fail(ToolError.TOOL, "CLICK_UNCONFIRMED", "the server did not send the window back within "
                        + WAIT_MS + "ms of the click, so what the slots hold now is not known", true);
            }
        }));
    }
}
