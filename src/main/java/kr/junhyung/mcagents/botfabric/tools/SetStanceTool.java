package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;

/**
 * Sneak and sprint, which change what a server lets the bot do: a sneaking player does not
 * trigger a pressure plate, and some plugins read the crouch to mean something.
 *
 * <p>A field left null is left alone. The server sends null when the caller said nothing, so
 * "start sneaking" does not quietly stop a sprint that was already running.
 */
public final class SetStanceTool extends ReadTool {

    public SetStanceTool() {
        super("set-stance");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        LocalPlayer player = Mc.requirePlayer();

        if (!args.get("sneak").isJsonNull()) {
            player.setShiftKeyDown(args.get("sneak").getAsBoolean());
        }
        if (!args.get("sprint").isJsonNull()) {
            player.setSprinting(args.get("sprint").getAsBoolean());
        }
        return new JsonObject();
    }

    @Override
    protected String summary(JsonObject data) {
        return "stance set";
    }
}
