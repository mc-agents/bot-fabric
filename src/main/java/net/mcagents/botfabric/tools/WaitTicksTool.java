package net.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import net.mcagents.botfabric.rpc.CallContext;
import net.mcagents.botfabric.task.Task;
import net.mcagents.botfabric.task.TaskScheduler;
import net.mcagents.botfabric.tool.Args;
import net.mcagents.botfabric.tool.Tool;
import net.mcagents.botfabric.tool.ToolException;

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
        return "sha256:b2835c343bcf281db2c56d1f1b5c3a25624f0d7d0ce68425286991b7d70d55ec";
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
            call.ok("waited " + ticks + " ticks");
            return true;
        }
    }
}
