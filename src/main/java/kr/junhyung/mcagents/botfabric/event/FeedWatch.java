package kr.junhyung.mcagents.botfabric.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The action bar, the titles and the effects, for a task that has to react to a line the moment it
 * arrives.
 *
 * <p>A server's timing window can be two ticks wide, and a caller who waits on the feed and then
 * presses is a round trip through mcp-server too late. Packets are handled on the client thread
 * before the tick that reads the keys, so a listener that presses a key here is heard on that tick.
 */
public final class FeedWatch {

    private static final Set<BiConsumer<String, String>> listening = ConcurrentHashMap.newKeySet();

    private FeedWatch() {
    }

    /** A line on {@code kind}, as the text a pattern is matched against. Client thread. */
    public static void saw(String kind, String text) {
        for (BiConsumer<String, String> listener : listening) {
            listener.accept(kind, text);
        }
    }

    public static void listen(BiConsumer<String, String> listener) {
        listening.add(listener);
    }

    public static void forget(BiConsumer<String, String> listener) {
        listening.remove(listener);
    }
}
