package kr.junhyung.mcagents.botfabric.event;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The chat lines still on screen, kept so one of them can be clicked.
 *
 * <p>A server writes a quest's choices, a shop's items and half its menus as chat with a click
 * event on them. The feed carries the component to mcp-server, so an agent can read what is on
 * offer; pressing it needs the component on this side, because the event lives inside it and a
 * flattened string has none of it.
 *
 * <p>Bounded and thrown away in order, like the client's own history. What is off the screen is
 * not clickable for a player either.
 */
public final class ChatLines {

    /** A hundred lines is more than a client shows and less than anything worth measuring. */
    private static final int KEPT = 100;

    private static final Deque<Component> lines = new ArrayDeque<>();

    private ChatLines() {
    }

    public static void heard(Component line) {
        synchronized (lines) {
            lines.addFirst(line);
            while (lines.size() > KEPT) {
                lines.removeLast();
            }
        }
    }

    /** Newest first, which is the order a search should read them in. */
    public static List<Component> recent() {
        synchronized (lines) {
            return List.copyOf(lines);
        }
    }

    public static void forget() {
        synchronized (lines) {
            lines.clear();
        }
    }
}
