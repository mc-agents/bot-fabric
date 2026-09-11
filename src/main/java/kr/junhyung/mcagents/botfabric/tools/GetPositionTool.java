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

            JsonObject data = new JsonObject();
            data.addProperty("x", block.getX());
            data.addProperty("y", block.getY());
            data.addProperty("z", block.getZ());
            data.addProperty("exactX", player.getX());
            data.addProperty("exactY", player.getY());
            data.addProperty("exactZ", player.getZ());
            data.addProperty("yaw", player.getYRot());
            data.addProperty("pitch", player.getXRot());
            data.addProperty("dimension", player.level().dimension().identifier().toString());

            call.ok("standing at %d, %d, %d in %s"
                    .formatted(block.getX(), block.getY(), block.getZ(),
                            player.level().dimension().identifier()), data);
        });
    }
}
