package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * What the open window holds, or the fact that there is not one.
 *
 * <p>Nothing being open is a state, so it travels as {@code window: null} rather than as a refusal
 * with this class's own wording. Comparing the two kinds of bot is what found that they each had
 * one.
 */
public final class ReadWindowTool extends ReadTool {

    public ReadWindowTool() {
        super("read-window");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        JsonObject data = new JsonObject();
        AbstractContainerScreen<?> container = Windows.open();

        data.add("window", container == null ? JsonNull.INSTANCE : Windows.describe(container));

        return data;
    }
}
