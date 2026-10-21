package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

/**
 * Where the open dialog's body can be pressed or hovered, read off the screen as it is laid out now.
 *
 * <p>No dialog being open is a state, so it travels as {@code dialog: null} with the screen that is
 * open instead, the way read-window says no window is.
 */
public final class ReadDialogBodyTool extends ReadTool {

    public ReadDialogBodyTool() {
        super("read-dialog-body");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Screen screen = Mc.screen();
        JsonObject data = new JsonObject();

        data.addProperty("screen", screen == null ? null : screen.getClass().getSimpleName());
        data.add("dialog", screen instanceof DialogScreen<?> dialog
                ? DialogBody.json(DialogBody.read(dialog))
                : JsonNull.INSTANCE);

        return data;
    }
}
