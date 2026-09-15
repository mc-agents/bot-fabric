package kr.junhyung.mcagents.botfabric.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import kr.junhyung.mcagents.botfabric.text.Readings;

/**
 * The action bar, the titles and the effects, for a task that has to react to a line the moment it
 * arrives.
 *
 * <p>A server's timing window can be two ticks wide, and a caller who waits on the feed and then
 * presses is a round trip through mcp-server too late. Packets are handled on the client thread
 * before the tick that reads the keys, so a listener that presses a key here is heard on that tick.
 */
public final class FeedWatch {

    private static final Set<BiConsumer<String, Readings>> listening = ConcurrentHashMap.newKeySet();

    private FeedWatch() {
    }

    /** A line on {@code kind}, every way a pattern can read it. Client thread. */
    public static void saw(String kind, Readings line) {
        for (BiConsumer<String, Readings> listener : listening) {
            listener.accept(kind, line);
        }
    }

    public static void listen(BiConsumer<String, Readings> listener) {
        listening.add(listener);
    }

    public static void forget(BiConsumer<String, Readings> listener) {
        listening.remove(listener);
    }
}
