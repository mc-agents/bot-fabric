package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import java.util.List;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;

/**
 * Several inputs in one call, spaced by the bot's own tick loop rather than by round trips.
 *
 * <p>Two tool calls land a second or more apart, which is the wrong side of every cooldown and
 * double-click window a server has. The steps are parsed and refused whole before anything is
 * sent -- {@link Step} -- and then {@link SequenceTask} runs them, one starting on the tick the
 * one before it ended.
 */
public final class RunInputsTool implements Tool {

    private final TaskScheduler scheduler;

    public RunInputsTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "run-inputs";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        List<Step> steps = Step.parse(args.get("steps"));
        scheduler.submit(new SequenceTask(steps, new Args(args).integer("timeoutMs", 10_000)), call);
    }
}
