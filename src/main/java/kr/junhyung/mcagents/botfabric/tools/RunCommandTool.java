package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.EventPump;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.Tool;

import java.util.List;

public final class RunCommandTool implements Tool {
    private final TaskScheduler scheduler;
    private final EventPump events;

    public RunCommandTool(TaskScheduler scheduler, EventPump events) {
        this.scheduler = scheduler;
        this.events = events;
    }

    @Override
    public String name() {
        return "run-command";
    }

    @Override
    public String argsHash() {
        return "sha256:b1a77f445106644a745cf0e35390a277802d4df8b08764e053bd18e0d26ca827";
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        String command = parsed.string("command");
        long collectMs = parsed.integer("collectMs", 1000);
        scheduler.submit(new RunCommandTask(events, command, collectMs), call);
    }

    private static final class RunCommandTask implements Task {
        private final EventPump events;
        private final String command;
        private final long collectMs;

        private long mark;
        private long sentAt;

        private RunCommandTask(EventPump events, String command, long collectMs) {
            this.events = events;
            this.command = command;
            this.collectMs = collectMs;
        }

        @Override
        public String name() {
            return "run-command";
        }

        @Override
        public void start(CallContext call) {
            mark = events.mark();
            Mc.requireConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
            sentAt = System.currentTimeMillis();
        }

        @Override
        public boolean tick(CallContext call) {
            if (System.currentTimeMillis() - sentAt < collectMs) {
                return false;
            }
            List<String> replies = events.since(mark);
            JsonArray lines = new JsonArray();
            replies.forEach(lines::add);

            JsonObject data = new JsonObject();
            data.addProperty("command", command);
            data.add("lines", lines);

            String text = replies.isEmpty()
                    ? "ran " + command + "; the server said nothing in " + collectMs + "ms"
                    : String.join("\n", replies);
            call.ok(text, data);
            return true;
        }
    }
}
