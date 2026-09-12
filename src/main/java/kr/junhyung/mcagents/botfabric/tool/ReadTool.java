package kr.junhyung.mcagents.botfabric.tool;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;

/**
 * A tool that reads client state and answers in one tick.
 *
 * <p>What every one of them has in common: the hash comes from the catalogue, the work happens on
 * the client thread because Minecraft's objects belong to it, and the answer is a DTO whose shape
 * the catalogue's {@code resultSchema} decides. mcp-server writes the sentence.
 *
 * <p>Anything that takes more than a tick -- walking, digging, waiting for a window -- is a
 * {@link kr.junhyung.mcagents.botfabric.task.Task} instead, and does not belong here.
 */
public abstract class ReadTool implements Tool {

    private final String name;

    protected ReadTool(String name) {
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String argsHash() {
        return CatalogHashes.of(name);
    }

    @Override
    public final void invoke(CallContext call, JsonObject args) {
        Mc.immediate(call, () -> {
            JsonObject data = read(args);
            call.ok(summary(data), data);
        });
    }

    /** The DTO, shaped by the catalogue. Runs on the client thread. */
    protected abstract JsonObject read(JsonObject args);

    /**
     * One line for somebody reading the wire by hand. Never what a caller is shown: the server
     * renders that from the DTO, so that two kinds of bot cannot describe one state two ways.
     */
    protected String summary(JsonObject data) {
        return name;
    }
}
