package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.FeedWatch;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.text.Readings;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/**
 * The steps of run-inputs, run tick by tick inside the bot.
 *
 * <p>Tick 0 is the tick the first step starts on, and a step starts on the very tick the step
 * before it ended, so what the answer reports as the ticks between two inputs is what the game
 * saw between them. A press ends the tick after it is let go, so that the game reads it up before
 * the next input; a click ends when the server has sent the window back, the way click-slot
 * answers; a command ends on its own tick; a wait ends that many ticks after it started; a waitFor
 * ends at the end of the tick its line arrived on. Steps that end on the tick they start run on
 * inside one tick.
 *
 * <p>The first step the game refuses stops the sequence, and the call is still answered: what the
 * steps before it did is why a caller batched them. The whole of it is bounded by timeoutMs, and
 * a step cut by that is let go of and reported as cut.
 */
final class SequenceTask implements Task {

    private static final long TICK_MS = 50;

    /**
     * The feed lines seen since the sequence started, and which of them a waitFor may still take.
     * A waitFor looks from where the step before it started, so a reply the server sends while
     * that step is still settling is not missed, and never at a line an earlier waitFor took.
     */
    static final class Lines {
        private record Line(String kind, Readings reading) {
        }

        private final List<Line> seen = new ArrayList<>();
        private int mark;
        private int consumed;

        void add(String kind, Readings reading) {
            seen.add(new Line(kind, reading));
        }

        /** A step starts: where a waitFor starting now looks from, with this start kept for the next one. */
        int begin() {
            int from = Math.max(mark, consumed);
            mark = seen.size();
            return from;
        }

        /** The first line from {@code from} on that the watch matches, taken; or null while there is none. */
        String take(int from, Watch watch) {
            for (int i = Math.max(from, consumed); i < seen.size(); i++) {
                Line line = seen.get(i);
                String matched = watch.matched(line.kind(), line.reading());
                if (matched != null) {
                    consumed = i + 1;
                    return matched;
                }
            }
            return null;
        }
    }

    private final List<Step> steps;
    private final int timeoutMs;
    private final Lines lines = new Lines();
    private final BiConsumer<String, Readings> listener = lines::add;
    private final JsonArray results = new JsonArray();

    private long startedNanos;
    private int tick = -1;
    private int next;
    private Runner current;
    private int ran;
    private int ticks;

    SequenceTask(List<Step> steps, int timeoutMs) {
        this.steps = steps;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String name() {
        return "run-inputs";
    }

    static long atLeastMs(List<Step> steps) {
        return steps.stream().mapToLong(step -> step.atLeastTicks() * TICK_MS).sum();
    }

    @Override
    public void start(CallContext call) {
        Mc.requirePlayer();
        long atLeastMs = atLeastMs(steps);
        if (atLeastMs > timeoutMs) {
            throw ToolException.refused("TOO_LONG", "the " + steps.size() + " steps take at least " + atLeastMs
                    + "ms, longer than timeoutMs (" + timeoutMs + "ms).");
        }
        startedNanos = System.nanoTime();
        FeedWatch.listen(listener);
    }

    @Override
    public boolean tick(CallContext call) throws Exception {
        tick++;
        if (elapsedMs() > timeoutMs) {
            return cut(call);
        }
        while (current != null || next < steps.size()) {
            if (current == null) {
                current = runner(steps.get(next++));
                try {
                    current.start(call);
                } catch (ToolException refusal) {
                    return refused(call, refusal);
                }
            }
            boolean ended;
            try {
                ended = current.step(call);
            } catch (ToolException refusal) {
                return refused(call, refusal);
            }
            if (!ended) {
                return false;
            }
            if (current.error != null) {
                return refused(call, current.error);
            }
            finish(call, null);
            ran++;
        }
        return answer(call, "done");
    }

    @Override
    public void cleanup(CallContext call) {
        FeedWatch.forget(listener);
        if (current != null) {
            current.cleanup(call);
        }
    }

    private Runner runner(Step step) {
        int from = lines.begin();
        return switch (step) {
            case Step.Press press -> new PressRunner(press, tick, timeoutMs);
            case Step.Click click -> new ClickRunner(click, tick);
            case Step.Command command -> new CommandRunner(command, tick);
            case Step.Wait wait -> new WaitRunner(wait, tick);
            case Step.WaitFor waitFor -> new WaitForRunner(waitFor, tick, from);
        };
    }

    private boolean refused(CallContext call, ToolException refusal) {
        finish(call, refusal);
        return answer(call, "refused");
    }

    private boolean cut(CallContext call) {
        if (current != null) {
            finish(call, ToolException.refused("TIMEOUT", "timeoutMs (" + timeoutMs + ") ran out during this step"));
        } else {
            ticks = tick;
        }
        return answer(call, "timeout");
    }

    /** The step in flight is over on this tick, well or with {@code error}: let go of and written down. */
    private void finish(CallContext call, ToolException error) {
        current.cleanup(call);

        JsonObject entry = new JsonObject();
        entry.addProperty("kind", current.step.kind());
        entry.addProperty("asked", current.step.asked());
        entry.addProperty("startedTick", current.startedTick);
        entry.addProperty("endedTick", tick);
        for (String kind : Step.KINDS) {
            entry.add(kind, kind.equals(current.step.kind()) && error == null ? current.dto() : JsonNull.INSTANCE);
        }
        if (error == null) {
            entry.add("error", JsonNull.INSTANCE);
        } else {
            JsonObject failed = new JsonObject();
            failed.addProperty("code", error.code());
            failed.addProperty("message", error.getMessage());
            entry.add("error", failed);
        }
        results.add(entry);
        ticks = tick;
        current = null;
    }

    private boolean answer(CallContext call, String stopped) {
        JsonObject data = new JsonObject();
        data.addProperty("asked", steps.size());
        data.addProperty("ran", ran);
        data.addProperty("ticks", ticks);
        data.addProperty("stopped", stopped);
        data.add("steps", results);

        call.ok("ran " + ran + " of " + steps.size() + " steps in " + ticks + " ticks, " + stopped, data);
        return true;
    }

    private long elapsedMs() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /** One step in flight. A refusal is thrown from start or step, or left in {@code error} by a later answer. */
    private abstract static class Runner {
        final Step step;
        final int startedTick;
        ToolException error;

        Runner(Step step, int startedTick) {
            this.step = step;
            this.startedTick = startedTick;
        }

        void start(CallContext call) throws Exception {
        }

        abstract boolean step(CallContext call) throws Exception;

        abstract JsonElement dto();

        void cleanup(CallContext call) {
        }
    }

    /**
     * press-input's own task, one press with no after or until, ending the tick after it let go.
     *
     * <p>The press is given the sequence's whole timeoutMs rather than what is left of it: the
     * sequence's TOO_LONG has already weighed the press, and its own elapsed check runs first on
     * every tick, so running out is the sequence's to report and never the press's to refuse.
     */
    private static final class PressRunner extends Runner {
        private final PressTask task;
        private JsonObject pressed;
        private boolean released;

        PressRunner(Step.Press press, int startedTick, int timeoutMs) {
            super(press, startedTick);
            task = new PressTask(press.key(), press.slot(), press.holdTicks(), 1, 1, null, null, timeoutMs,
                    data -> pressed = data);
        }

        @Override
        void start(CallContext call) throws Exception {
            task.start(call);
        }

        @Override
        boolean step(CallContext call) throws Exception {
            if (released) {
                return true;
            }
            released = task.tick(call);
            return false;
        }

        @Override
        JsonElement dto() {
            return pressed;
        }

        @Override
        void cleanup(CallContext call) {
            task.cleanup(call);
        }
    }

    /** click-slot's own click, ending on the tick the server's answer is seen. */
    private static final class ClickRunner extends Runner {
        private JsonObject clicked;

        ClickRunner(Step.Click click, int startedTick) {
            super(click, startedTick);
        }

        @Override
        void start(CallContext call) {
            ClickSlotTool.click(new ServerResync.Reply() {
                @Override
                public void ok(JsonObject data) {
                    clicked = data;
                }

                @Override
                public void fail(ToolException refusal) {
                    error = refusal;
                }
            }, ((Step.Click) step).args());
        }

        @Override
        boolean step(CallContext call) {
            return clicked != null || error != null;
        }

        @Override
        JsonElement dto() {
            return clicked;
        }
    }

    private static final class CommandRunner extends Runner {
        CommandRunner(Step.Command command, int startedTick) {
            super(command, startedTick);
        }

        @Override
        void start(CallContext call) {
            Mc.requireConnection().sendCommand(((Step.Command) step).text().substring(1));
        }

        @Override
        boolean step(CallContext call) {
            return true;
        }

        @Override
        JsonElement dto() {
            return new JsonPrimitive(((Step.Command) step).text());
        }
    }

    private final class WaitRunner extends Runner {
        WaitRunner(Step.Wait wait, int startedTick) {
            super(wait, startedTick);
        }

        @Override
        boolean step(CallContext call) {
            return tick >= startedTick + ((Step.Wait) step).ticks();
        }

        @Override
        JsonElement dto() {
            return new JsonPrimitive(((Step.Wait) step).ticks());
        }
    }

    private final class WaitForRunner extends Runner {
        private final int from;
        private final long startedNanos = System.nanoTime();
        private String matched;
        private long waitedMs;

        WaitForRunner(Step.WaitFor waitFor, int startedTick, int from) {
            super(waitFor, startedTick);
            this.from = from;
        }

        @Override
        boolean step(CallContext call) {
            matched = lines.take(from, ((Step.WaitFor) step).watch());
            if (matched == null) {
                return false;
            }
            waitedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
            return true;
        }

        @Override
        JsonElement dto() {
            JsonObject waited = ((Step.WaitFor) step).watch().describe(matched);
            waited.addProperty("waitedMs", waitedMs);
            return waited;
        }
    }
}
