package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
 * <p>Not the creative inventory. Its clicks are not container clicks and the server sends nothing
 * back for them, so it is answered as the client has it, which in creative is what the server takes.
 */
public final class ServerResync {

    private static final long WAIT_MS = 2_000;

    /** Client thread only. */
    private static final List<Waiting> WAITING = new ArrayList<>();

    private record Waiting(int containerId, Runnable answer) {
    }

    private ServerResync() {
    }

    /** Called on the client thread once the whole of a window has been applied. */
    public static void arrived(int containerId) {
        Iterator<Waiting> each = WAITING.iterator();
        while (each.hasNext()) {
            Waiting waiting = each.next();
            if (waiting.containerId() == containerId) {
                each.remove();
                waiting.answer().run();
            }
        }
    }

    /** Clicks, then answers with the DTO read after the server's copy of the window is in. Client thread only. */
    static void click(CallContext call, String tool, AbstractContainerScreen<?> screen, Runnable click,
            Supplier<JsonObject> answer) {
        if (screen instanceof CreativeModeInventoryScreen) {
            click.run();
            call.ok(tool, answer.get());
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        Waiting waiting = new Waiting(menu.containerId, () -> call.ok(tool, answer.get()));
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
