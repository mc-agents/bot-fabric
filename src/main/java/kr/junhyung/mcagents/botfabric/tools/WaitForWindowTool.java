package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.text.Readings;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Wait until a window opens, and answer with it. A window already open and matching answers at
 * once, which is what makes the tool usable after the thing that opens one has been run.
 *
 * <p>Nothing opening in time is a state and not a timeout: the answer is {@code window: null} with
 * the pattern and the wait beside it, so mcp-server can say what was waited on. A refusal here
 * would be indistinguishable from the deadline the server applies to the call itself.
 *
 * <p>The pattern is a JavaScript regular expression in the catalogue because the first bot to
 * implement this was in TypeScript. Java's {@link Pattern} accepts the subset a caller writes for
 * a window title, and {@code find} is the semantics of {@code RegExp.test}. It is matched against
 * the title every way a caller can have read it -- as read-window shows it, font labels and all,
 * and as plain text -- through {@link Readings}.
 */
public final class WaitForWindowTool implements Tool {

    private final TaskScheduler scheduler;

    public WaitForWindowTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "wait-for-window";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        String source = args.get("titlePattern") == null || args.get("titlePattern").isJsonNull()
                ? null
                : new Args(args).string("titlePattern");

        scheduler.submit(new WaitTask(source, compile(source), new Args(args).integer("timeoutMs", 10_000)), call);
    }

    /**
     * The catalogue calls this a JavaScript regular expression because the first bot to implement
     * the tool was in TypeScript. Java's syntax accepts the subset a caller writes for a window
     * title; anything it does not is a bad argument and is said so rather than silently never
     * matching.
     */
    static Pattern compile(String source) {
        if (source == null) {
            return null;
        }
        try {
            return Pattern.compile(source);
        } catch (PatternSyntaxException invalid) {
            throw ToolException.badArgs("\"" + source + "\" is not a valid regular expression: "
                    + invalid.getDescription());
        }
    }

    /**
     * {@code find} and not {@code matches}: that is the semantics of JavaScript's RegExp.test, so an
     * unanchored pattern matches part of a title on both kinds of bot.
     */
    static boolean titleMatches(Readings title, Pattern pattern) {
        return pattern == null || title.matches(pattern);
    }

    private static final class WaitTask implements Task {
        private final String source;
        private final Pattern pattern;
        private final long timeoutMs;
        private final long startedNanos = System.nanoTime();

        private WaitTask(String source, Pattern pattern, long timeoutMs) {
            this.source = source;
            this.pattern = pattern;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "wait-for-window";
        }

        @Override
        public boolean tick(CallContext call) {
            AbstractContainerScreen<?> container = Windows.open();

            if (container != null && titleMatches(Readings.of(container.getTitle()), pattern)) {
                answer(call, Windows.describe(container));
                return true;
            }
            if ((System.nanoTime() - startedNanos) / 1_000_000L < timeoutMs) {
                return false;
            }
            answer(call, null);
            return true;
        }

        private void answer(CallContext call, JsonObject window) {
            JsonObject data = new JsonObject();
            data.add("titlePattern", source == null ? JsonNull.INSTANCE : new JsonPrimitive(source));
            data.addProperty("timeoutMs", timeoutMs);
            data.add("window", window == null ? JsonNull.INSTANCE : window);

            call.ok(window == null ? "nothing opened within " + timeoutMs + "ms" : "window opened", data);
        }
    }
}
