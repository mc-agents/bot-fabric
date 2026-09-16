package kr.junhyung.mcagents.botfabric.session;

import net.minecraft.network.Connection;

/**
 * The connection the client is on, whatever phase it is in.
 *
 * <p>In a world the level holds it and during the login the connect screen does; in configuration
 * nothing public does. A dialog the server shows from there replaces the connect screen, and a
 * proxy moving the bot between backends puts up the reconfiguration screen with the connection in
 * a private field of its own. The one thing every configuration and play phase has is the common
 * listener, so the mixin hands its connection here as it is made, and a leave that finds no level
 * and no connect screen closes this one.
 */
public final class Connections {

    private static volatile Connection current;

    private Connections() {
    }

    public static void noticed(Connection connection) {
        current = connection;
    }

    /** The connection the last listener was made on, or null when it has since closed. */
    public static Connection current() {
        Connection connection = current;
        return connection != null && connection.isConnected() ? connection : null;
    }
}
