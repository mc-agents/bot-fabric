package kr.junhyung.mcagents.botfabric.task;

import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric/task");

    private final Queue<Running> incoming = new ConcurrentLinkedQueue<>();
    private final List<Running> running = new ArrayList<>();

    public void submit(Task task, CallContext call) {
        incoming.add(new Running(task, call));
    }

    public int size() {
        return running.size();
    }

    public void tick() {
        Running queued;
        while ((queued = incoming.poll()) != null) {
            running.add(queued);
        }

        Iterator<Running> iterator = running.iterator();
        while (iterator.hasNext()) {
            Running entry = iterator.next();
            if (step(entry)) {
                iterator.remove();
                finish(entry);
            }
        }
    }

    public void abortAll(String reason) {
        Running queued;
        while ((queued = incoming.poll()) != null) {
            running.add(queued);
        }
        for (Running entry : running) {
            entry.call.fail(ToolError.BOT, "LINK_LOST", reason, true);
            finish(entry);
        }
        running.clear();
    }

    private boolean step(Running entry) {
        if (entry.call.settled()) {
            return true;
        }
        try {
            if (!entry.started) {
                entry.started = true;
                entry.task.start(entry.call);
                if (entry.call.settled()) {
                    return true;
                }
            }
            if (entry.task.tick(entry.call)) {
                if (!entry.call.settled()) {
                    entry.call.fail(ToolError.INTERNAL, "NO_RESULT",
                            entry.task.name() + " finished without producing a result", false);
                }
                return true;
            }
            return entry.call.settled();
        } catch (Throwable thrown) {
            entry.call.fail(thrown);
            return true;
        }
    }

    private void finish(Running entry) {
        if (entry.cleaned) {
            return;
        }
        entry.cleaned = true;
        try {
            entry.task.cleanup(entry.call);
        } catch (Throwable thrown) {
            LOGGER.error("cleanup of {} threw", entry.task.name(), thrown);
        }
    }

    private static final class Running {
        private final Task task;
        private final CallContext call;
        private boolean started;
        private boolean cleaned;

        private Running(Task task, CallContext call) {
            this.task = task;
            this.call = call;
        }
    }
}
