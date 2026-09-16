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

    private Disconnects() {
    }

    public static void listen(Session listener) {
        session = listener;
    }

    /** Called on the client thread once the client has torn the world down over it. */
    public static void noticed(String reason) {
        Session listener = session;
        if (listener != null) {
            listener.noticeDisconnect(reason);
        }
    }
}
