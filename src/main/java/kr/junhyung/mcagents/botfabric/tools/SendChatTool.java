package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class SendChatTool implements Tool {
    @Override
    public String name() {
        return "send-chat";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        String message = new Args(args).string("message");
        Mc.immediate(call, () -> {
            ClientPacketListener connection = Mc.requireConnection();
            if (message.startsWith("/")) {
                connection.sendCommand(message.substring(1));
                call.ok("ran " + message);
            } else {
                connection.sendChat(message);
                call.ok("said " + message);
            }
        });
    }
}
