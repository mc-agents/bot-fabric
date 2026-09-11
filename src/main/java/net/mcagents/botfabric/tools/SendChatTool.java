package net.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import net.mcagents.botfabric.Mc;
import net.mcagents.botfabric.rpc.CallContext;
import net.mcagents.botfabric.tool.Args;
import net.mcagents.botfabric.tool.Tool;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class SendChatTool implements Tool {
    @Override
    public String name() {
        return "send-chat";
    }

    @Override
    public String argsHash() {
        return "sha256:5c7dd5f9ce17e6ef4722a41c75a474b6dddbbccab91d61afb2ada4ef8ff46829";
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
