package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/** A key pressed tick by tick, the way the keyboard and the mouse press it: {@link PressTask}. */
public final class PressInputTool implements Tool {

    private final TaskScheduler scheduler;

    public PressInputTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "press-input";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        Key key = Key.of(parsed.string("key"));
        Integer slot = args.get("slot") == null || args.get("slot").isJsonNull() ? null : parsed.integer("slot", 0);

        if (key == Key.HOTBAR && slot == null) {
            throw ToolException.refused("NO_SLOT", "hotbar needs slot, 0 being the leftmost hotbar slot.");
        }

        scheduler.submit(new PressTask(key, key == Key.HOTBAR ? slot : null,
                parsed.integer("holdTicks", 1), parsed.integer("repeat", 1), parsed.integer("intervalTicks", 1),
                Watch.of(args.get("after")), Watch.of(args.get("until")), parsed.integer("timeoutMs", 10_000),
                data -> call.ok(pressed(data), data)), call);
    }

    private static String pressed(JsonObject data) {
        return "pressed " + data.get("key").getAsString() + " " + data.get("presses").getAsInt() + " of "
                + data.get("repeat").getAsInt() + " time(s), " + data.get("stopped").getAsString();
    }
}
