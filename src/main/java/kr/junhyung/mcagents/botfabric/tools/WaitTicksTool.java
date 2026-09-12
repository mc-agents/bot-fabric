package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

public final class WaitTicksTool implements Tool {
    private final TaskScheduler scheduler;

    public WaitTicksTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "wait-ticks";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        int ticks = new Args(args).integer("ticks", -1);
        if (ticks < 1) {
            throw ToolException.badArgs("ticks must be at least 1");
        }
        scheduler.submit(new WaitTask(ticks), call);
    }

    private static final class WaitTask implements Task {
        private final int ticks;
        private int elapsed;

        private WaitTask(int ticks) {
            this.ticks = ticks;
        }

        @Override
        public String name() {
            return "wait-ticks";
        }

        @Override
        public boolean tick(CallContext call) {
            if (++elapsed < ticks) {
                return false;
            }
            call.ok("Waited " + ticks + " tick(s).");
            return true;
        }
    }
}
