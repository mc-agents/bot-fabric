package kr.junhyung.mcagents.botfabric.tool;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;

public interface Tool {
    String name();

    /** The catalogue's hash for this tool's arguments, or null when the catalogue has no entry. */
    String argsHash();

    void invoke(CallContext call, JsonObject args) throws Exception;
}
