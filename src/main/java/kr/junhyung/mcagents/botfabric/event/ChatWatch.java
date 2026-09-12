package kr.junhyung.mcagents.botfabric.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Chat, for a task that is waiting for a particular line.
 *
 * <p>A feed is pushed to mcp-server and forgotten; this is for the one thing a proxy only ever says
 * in chat. "That server does not exist" and "you are already connected to this server" are not
 * events, they are the answer to a command, and nothing else in the packet stream carries them.
 *
 * <p>Listeners are called on the network thread, so a listener does the smallest possible thing --
 * setting a field a tick will read -- and never touches the world.
 */
public final class ChatWatch {

    private static final Set<Consumer<String>> listening = ConcurrentHashMap.newKeySet();

    private ChatWatch() {
    }

    public static void heard(String line) {
        for (Consumer<String> listener : listening) {
            listener.accept(line);
        }
    }

    public static void listen(Consumer<String> listener) {
        listening.add(listener);
    }

    public static void forget(Consumer<String> listener) {
        listening.remove(listener);
    }
}
