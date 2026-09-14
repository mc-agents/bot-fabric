package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * What goes on the wire for each feed line: the valve, the sequence numbers, and the folding.
 *
 * <p>Kept apart from {@link EventPump}, which turns what the game hands over into lines, because
 * none of this needs a game and all of it is easy to get subtly wrong. The cadence and the valves
 * are the server's, from {@code helloOk}; until one arrives every feed is shut, which costs nothing
 * because there is no link to send on either.
 */
final class FeedFolder {

    /** The feeds a run is folded on. The same chat line, sound or toast twice is two things that happened. */
    private static final Set<String> FOLDED = Set.of("actionBar", "title", "dialog");

    /** A run nothing has repeated for this many flushes has stopped showing, whatever the server meant. */
    private static final long STALE_FLUSHES = 3;

    private final Consumer<JsonObject> out;
    private final LongSupplier clock;

    private long seq;
    private long flushMs = 1_000;
    private Map<String, Boolean> valves = Map.of();
    /** The run each folded feed has open. A title and a subtitle share the feed, so one replaces the other. */
    private final Map<String, Run> runs = new HashMap<>();

    FeedFolder(Consumer<JsonObject> out, LongSupplier clock) {
        this.out = out;
        this.clock = clock;
    }

    /** The server's cadence and valves. A feed the server did not name is shut, as it would be on the other kind of bot. */
    synchronized void configure(JsonObject helloOk) {
        JsonElement flush = helloOk.get("repeatFlushMs");
        if (flush != null && flush.isJsonPrimitive() && flush.getAsLong() > 0) {
            flushMs = flush.getAsLong();
        }

        Map<String, Boolean> given = new HashMap<>();
        JsonElement events = helloOk.get("events");
        if (events != null && events.isJsonObject()) {
            for (Map.Entry<String, JsonElement> feed : events.getAsJsonObject().entrySet()) {
                given.put(feed.getKey(), feed.getValue().isJsonPrimitive() && feed.getValue().getAsBoolean());
            }
        }
        valves = given;
    }

    synchronized boolean wants(String kind) {
        return valves.getOrDefault(kind, false);
    }

    /** A line for a feed, without seq and times. Dropped when the feed is shut. */
    synchronized void push(String kind, JsonObject line) {
        if (!wants(kind)) {
            return;
        }
        long now = clock.getAsLong();

        if (!FOLDED.contains(kind)) {
            JsonObject event = line.deepCopy();
            event.addProperty("seq", ++seq);
            event.addProperty("ts", now);
            event.addProperty("firstTs", now);
            event.addProperty("repeats", 1);
            event.addProperty("closed", true);
            out.accept(event);
            return;
        }

        /*
        An action bar redrawn twenty times a second is one thing showing, not twenty things
        happening. The run keeps its sequence number and every re-send reuses it, so mcp-server
        updates that line in place rather than waking a waiter on each frame.
        */
        Run run = runs.get(kind);
        if (run != null && run.line.equals(line)) {
            run.repeats++;
            run.seen = now;
            if (now - run.sent >= flushMs) {
                run.sent = now;
                out.accept(run.frame(false));
            }
            return;
        }

        if (run != null) {
            out.accept(run.frame(true));
        }
        Run opened = new Run(++seq, line, now);
        runs.put(kind, opened);
        out.accept(opened.frame(false));
    }

    /** Re-send the runs that are due, and close the ones nothing has repeated in a while. */
    synchronized void flush() {
        long now = clock.getAsLong();

        for (Iterator<Run> open = runs.values().iterator(); open.hasNext(); ) {
            Run run = open.next();

            if (now - run.seen >= flushMs * STALE_FLUSHES) {
                out.accept(run.frame(true));
                open.remove();
            } else if (now - run.sent >= flushMs) {
                run.sent = now;
                out.accept(run.frame(false));
            }
        }
    }

    /** The bot left the world, so nothing it was showing is showing any more. */
    synchronized void closeAll() {
        long now = clock.getAsLong();

        for (Run run : runs.values()) {
            run.seen = now;
            out.accept(run.frame(true));
        }
        runs.clear();
    }

    /** One folded run: the line as it was first seen, and how often and how lately it was seen again. */
    private static final class Run {
        private final long seq;
        private final JsonObject line;
        private final long first;
        private long seen;
        private long sent;
        private int repeats = 1;

        private Run(long seq, JsonObject line, long now) {
            this.seq = seq;
            this.line = line;
            this.first = now;
            this.seen = now;
            this.sent = now;
        }

        private JsonObject frame(boolean closed) {
            JsonObject event = line.deepCopy();
            event.addProperty("seq", seq);
            event.addProperty("ts", seen);
            event.addProperty("firstTs", first);
            event.addProperty("repeats", repeats);
            event.addProperty("closed", closed);
            return event;
        }
    }
}
