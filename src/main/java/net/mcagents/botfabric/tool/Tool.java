package net.mcagents.botfabric.tool;

import com.google.gson.JsonObject;
import net.mcagents.botfabric.rpc.CallContext;

public interface Tool {
    String name();

    String argsHash();

    void invoke(CallContext call, JsonObject args) throws Exception;
}
