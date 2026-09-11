package kr.junhyung.mcagents.botfabric.tool;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;

public interface Tool {
    String name();

    String argsHash();

    void invoke(CallContext call, JsonObject args) throws Exception;
}
