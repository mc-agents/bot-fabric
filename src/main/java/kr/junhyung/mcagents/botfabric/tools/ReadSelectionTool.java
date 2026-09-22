package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.worldedit.Cui;

/**
 * What WorldEdit has selected, asked of the server rather than read out of chat.
 *
 * <p>A {@link Cui} announcement is sent and the description that comes back is the answer. That is
 * a round trip and not a look at kept state, because the point of the tool is to know the selection
 * as it stands now: a {@code //pos1} the server has not finished handling yet has changed nothing
 * to describe, and an answer assembled from the last description would report the corner before it.
 *
 * <p>A server that never answers is a server without WorldEdit, or one whose WorldEdit does not
 * send CUI, and that is {@code supported: false} rather than a refusal -- the caller's next move is
 * to fall back to reading chat, which needs to be told the difference.
 */
public final class ReadSelectionTool implements Tool {

    /** How long the server is given to describe the selection before the answer says it did not. */
    private static final int DEFAULT_TIMEOUT_MS = 1_000;

    private final TaskScheduler scheduler;

    public ReadSelectionTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "read-selection";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new AskTask(new Args(args).integer("timeoutMs", DEFAULT_TIMEOUT_MS)), call);
    }

    private static final class AskTask implements Task {

        private final long timeoutMs;
        private final long startedNanos = System.nanoTime();
        private int mark;
        /** Whether a description with no corner in it has already been given a further tick to grow one. */
        private boolean waitedAgain;

        private AskTask(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "read-selection";
        }

        @Override
        public void start(CallContext call) {
            /* Nothing can be asked of a server the client is not in, and the refusal names that rather than the send. */
            Mc.requirePlayerEvenIfDead();
            mark = Cui.described();
            Cui.ask();
        }

        /**
         * One description is several messages -- the shape, then a corner each -- and the server
         * sends them together, so the tick that carries the first carries the rest. The corners are
         * the proof of that: having one is having a whole description, and the answer goes back on
         * the tick it arrives.
         *
         * <p>A description with no corner in it is the ambiguous one, since it reads the same
         * whether nothing is selected or the corners are a tick behind. That one waits a further
         * tick, and the wait costs nothing where it happens -- no box is put down over an empty
         * selection.
         */
        @Override
        public boolean tick(CallContext call) {
            if (Cui.described() > mark) {
                if (!Cui.corners().isEmpty() || waitedAgain) {
                    answer(call, true);
                    return true;
                }
                waitedAgain = true;
                return false;
            }
            if ((System.nanoTime() - startedNanos) / 1_000_000L < timeoutMs) {
                return false;
            }
            answer(call, false);
            return true;
        }

        private void answer(CallContext call, boolean supported) {
            JsonArray points = new JsonArray();

            if (supported) {
                for (Cui.Corner corner : Cui.corners()) {
                    JsonObject point = new JsonObject();

                    point.addProperty("index", corner.index());
                    point.addProperty("x", corner.at().getX());
                    point.addProperty("y", corner.at().getY());
                    point.addProperty("z", corner.at().getZ());
                    points.add(point);
                }
            }

            long volume = Cui.volume();
            JsonObject data = new JsonObject();

            data.addProperty("supported", supported);
            data.add("shape", supported && Cui.shape() != null ? new JsonPrimitive(Cui.shape()) : JsonNull.INSTANCE);
            data.add("points", points);
            data.add("volume", supported && volume >= 0 ? new JsonPrimitive(volume) : JsonNull.INSTANCE);

            call.ok(supported ? "selection described in " + points.size() + " point(s)"
                    : "no selection was described within " + timeoutMs + "ms", data);
        }
    }
}
