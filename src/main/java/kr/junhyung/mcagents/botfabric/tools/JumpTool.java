package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;

/** One jump, for a pressure plate or a jump pad a server put in the way. */
public final class JumpTool extends ReadTool {

    public JumpTool() {
        super("jump");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        LocalPlayer player = Mc.requirePlayer();

        /* Only from the ground: in mid-air this is a no-op the server would not have believed. */
        if (player.onGround()) {
            player.jumpFromGround();
        }
        return new JsonObject();
    }

    @Override
    protected String summary(JsonObject data) {
        return "jumped";
    }
}
