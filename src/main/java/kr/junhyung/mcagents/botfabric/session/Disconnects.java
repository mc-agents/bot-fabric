package kr.junhyung.mcagents.botfabric.session;

/**
 * Where the mixin hands the reason a connection ended.
 *
 * <p>A mixin cannot be given a constructor argument, so the session registers itself here when it
 * starts, the way the feeds do. Before that a disconnect has nobody to tell: the link that would
 * carry the report is not up either.
 */
public final class Disconnects {

    private static volatile Session session;

    private static volatile String told;

    private Disconnects() {
    }

    public static void listen(Session listener) {
        session = listener;
    }

    /**
     * What the server said as it closed the connection, taken from its packet as it arrives.
     *
     * <p>The client closes the channel first and keeps the reason only once the close is done, and
     * its own thread can notice the channel shut in between: it then reports the generic
     * "Disconnected", which is what a refused whitelist login read as. The packet is the one place
     * the server's words are certain to be.
     */
    public static void told(String reason) {
        told = reason;
    }

    /** The server's words when it gave any, else what the client made of the ending. */
    public static String said(String client) {
        String server = told;
        return server != null && !server.isBlank() ? server : client;
    }

    /**
     * A new connection begins with nothing said, so it cannot be refused in the last one's words.
     * Not when one ends: a join that ends in configuration hears of it through the listener first,
     * and the join reads the words after.
     */
    public static void forget() {
        told = null;
    }

    /** Called on the client thread once the client has torn the world down over it. */
    public static void noticed(String reason) {
        Session listener = session;
        if (listener != null) {
            listener.noticeDisconnect(said(reason));
        }
    }
}
