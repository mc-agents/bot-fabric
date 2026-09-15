package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
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
 * <p>A menu redrawn under the same id is a new window as well. A plugin that turns a page sends the
 * open-screen packet again with the id the window already has, and the client builds a new menu for
 * it while the old one keeps the client's prediction of the click. So a wait is keyed by the menu
 * object it clicked, not by the id, and a window's contents arriving for any other object under the
 * open id means the window was replaced. That is answered at the end of the client tick rather than
 * on the packet: the server sends its own full copy of the new window right behind the plugin's, in
 * the same batch, and the cursor is read after that copy has been applied. A redraw a plugin
 * schedules a tick later, or a timer's redraw already in flight when the click was made, is still
 * taken for the click's answer or missed; the packets do not say which click they answer.
 *
 * <p>Not the creative inventory. Its clicks are not container clicks and the server sends nothing
 * back for them, so it is answered as the client has it, which in creative is what the server takes.
 */
public final class ServerResync {

    private static final long WAIT_MS = 2_000;

    /** Client thread only. */
    private static final List<Waiting> WAITING = new ArrayList<>();

    /**
     * Where a click's answer goes: the call that made it, or one step of a sequence that answers
     * its call once for all of its steps.
     */
    interface Reply {
        void ok(JsonObject data);

        void fail(ToolException refusal);

        static Reply of(CallContext call, String tool) {
            return new Reply() {
                @Override
                public void ok(JsonObject data) {
                    call.ok(tool, data);
                }

                @Override
                public void fail(ToolException refusal) {
                    call.fail(refusal);
                }
            };
        }
    }

    /** {@code answer} is given the window the click left open, or JSON null when it is the one clicked. */
    private static final class Waiting {
        private final AbstractContainerMenu menu;
        private final Function<JsonElement, JsonObject> answer;
        private final Reply reply;
        private boolean replaced;

        private Waiting(AbstractContainerMenu menu, Function<JsonElement, JsonObject> answer, Reply reply) {
            this.menu = menu;
            this.answer = answer;
            this.reply = reply;
        }

        void answer(JsonElement window) {
            reply.ok(answer.apply(window));
        }
    }

    private ServerResync() {
    }

    /**
     * Called on the client thread once the whole of a window has been applied. The menu clicked
     * answers its own click; any other menu now open under that id replaced the one clicked, and is
     * answered once the tick's packets are all in.
     */
    public static void arrived(int containerId) {
        AbstractContainerMenu open = Mc.client().player == null ? null : Mc.client().player.containerMenu;
        if (open == null || open.containerId != containerId) {
            return;
        }
        for (Waiting waiting : List.copyOf(WAITING)) {
            if (waiting.menu == open) {
                WAITING.remove(waiting);
                waiting.answer(JsonNull.INSTANCE);
            } else {
                waiting.replaced = true;
            }
        }
    }

    /**
     * At the end of every client tick. A click whose window was replaced is answered with the window
     * open now; with none open it goes on waiting, for the window or for the timeout.
     */
    public static void tick() {
        AbstractContainerScreen<?> screen = Windows.open();
        if (screen == null) {
            return;
        }
        for (Waiting waiting : List.copyOf(WAITING)) {
            if (waiting.replaced) {
                WAITING.remove(waiting);
                waiting.answer(replacedBy(screen));
            }
        }
    }

    /** Called on the client thread when the server closes a window. */
    public static void closed(int containerId) {
        for (Waiting waiting : List.copyOf(WAITING)) {
            if (waiting.menu.containerId == containerId) {
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
    static void click(Reply reply, AbstractContainerScreen<?> screen, Runnable click,
            Function<JsonElement, JsonObject> answer) {
        if (screen instanceof CreativeModeInventoryScreen) {
            click.run();
            reply.ok(answer.apply(JsonNull.INSTANCE));
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        Waiting waiting = new Waiting(menu, answer, reply);
        WAITING.add(waiting);
        menu.incrementStateId();
        click.run();

        CompletableFuture.delayedExecutor(WAIT_MS, TimeUnit.MILLISECONDS).execute(() -> Mc.client().execute(() -> {
            if (WAITING.remove(waiting)) {
                reply.fail(new ToolException(ToolError.TOOL, "CLICK_UNCONFIRMED",
                        "the server did not send the window back within " + WAIT_MS
                                + "ms of the click, so what the slots hold now is not known", true));
            }
        }));
    }
}
