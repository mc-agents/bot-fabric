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

/**
 * Runs the tasks that take more than one tick, on the client thread and nowhere else.
 *
 * <p>{@code running} belongs to that thread. Everything another thread wants to do to it arrives
 * as a queue the tick drains, because a list touched from two threads is the whole reason this
 * class exists: the RPC reader calling {@link #abortAll} while the render thread was iterating
 * took the client down with a ConcurrentModificationException, and a dropped link is exactly when
 * that happens.
 */
public final class TaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric/task");

    private final Queue<Running> incoming = new ConcurrentLinkedQueue<>();
    private final Queue<String> aborts = new ConcurrentLinkedQueue<>();
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

        String reason = aborts.poll();
        if (reason != null) {
            while (aborts.poll() != null) {
                // One abort settles everything; the rest are the same event arriving again.
            }
            abort(reason);
            return;
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

    /**
     * Fail everything in flight, from any thread. The work happens on the next tick: a caller here
     * is usually the RPC reader noticing the link went, and it must not touch what the client
     * thread is iterating.
     */
    public void abortAll(String reason) {
        aborts.add(reason);
    }

    private void abort(String reason) {
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
