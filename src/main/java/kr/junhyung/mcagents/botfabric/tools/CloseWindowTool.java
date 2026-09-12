package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Close whatever window is open, so the next thing that opens one is not refused.
 *
 * <p>Nothing being open is a state and not a mistake: asking to close nothing is a no-op. Each
 * kind of bot used to refuse it with its own wording, which is how the two came to describe one
 * state two ways.
 */
public final class CloseWindowTool extends ReadTool {

    public CloseWindowTool() {
        super("close-window");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        JsonObject data = new JsonObject();

        if (!(Mc.screen() instanceof AbstractContainerScreen<?> container)) {
            data.add("closed", JsonNull.INSTANCE);
            return data;
        }

        data.addProperty("closed", container.getTitle().getString());
        data.add("closedComponent", Segments.raw(container.getTitle()));
        Mc.requirePlayer().closeContainer();

        return data;
    }
}
