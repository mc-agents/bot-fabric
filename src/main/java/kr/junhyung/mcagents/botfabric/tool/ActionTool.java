package kr.junhyung.mcagents.botfabric.tool;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;

/**
 * A tool that changes something and answers in one tick with a sentence about what it changed.
 *
 * <p>The opposite end from {@link ReadTool}: a reader sends a DTO and lets mcp-server find the
 * words, because two kinds of bot must not describe one state two ways. A tool that reports on an
 * action sends no DTO -- the catalogue marks it unstructured -- so the sentence is the answer, and
 * the two kinds have to write the same one. bot-mineflayer's wording is the one to match.
 *
 * <p>Anything that takes more than a tick is a {@link kr.junhyung.mcagents.botfabric.task.Task}.
 */
public abstract class ActionTool implements Tool {

    private final String name;

    protected ActionTool(String name) {
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
        Mc.immediate(call, () -> call.ok(act(args)));
    }

    /** What was done, in the words the other kind of bot uses. Runs on the client thread. */
    protected abstract String act(JsonObject args);
}
