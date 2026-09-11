package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class GetPositionTool implements Tool {
    @Override
    public String name() {
        return "get-position";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Mc.immediate(call, () -> {
            LocalPlayer player = Mc.requirePlayer();
            BlockPos block = player.blockPosition();

            /*
            The shape is the catalogue's resultSchema and not this class's. It used to send x, y
            and z at the top level, so mcp-server's renderer -- which reads "position" -- answered
            "Position: null" about a bot that was standing somewhere perfectly definite.

            The block the player is in, floored. Which block an entity occupies is game knowledge,
            and truncating a negative coordinate towards zero names the block next door.
            */
            JsonObject position = new JsonObject();
            position.addProperty("x", block.getX());
            position.addProperty("y", block.getY());
            position.addProperty("z", block.getZ());

            JsonObject data = new JsonObject();
            data.add("position", position);

            call.ok("standing at %d, %d, %d in %s"
                    .formatted(block.getX(), block.getY(), block.getZ(),
                            player.level().dimension().identifier()), data);
        });
    }
}
