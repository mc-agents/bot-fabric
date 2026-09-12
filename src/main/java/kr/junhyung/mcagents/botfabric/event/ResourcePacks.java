package kr.junhyung.mcagents.botfabric.event;

/**
 * What became of the server's resource pack.
 *
 * <p>A server that draws its interface with custom glyphs has none of it without the pack, and the
 * client carries on regardless: every glyph becomes the missing-character box. A screenshot then
 * looks like a broken interface rather than like a pack that never arrived, and that is a day spent
 * on the wrong bug. So the outcome the client reports to the server is kept here, and the tool that
 * hands over a picture says so.
 *
 * <p>Written by the network thread as the client answers the server, read by whatever is taking the
 * picture.
 */
public final class ResourcePacks {

    private static volatile String failure;

    private ResourcePacks() {
    }

    /** The client's own verdict, as it goes back to the server. */
    public static void reported(String action) {
        switch (action) {
            case "SUCCESSFULLY_LOADED" -> failure = null;
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DECLINED", "DISCARDED" ->
                    failure = action;
            default -> {
                /* ACCEPTED and DOWNLOADED are steps on the way, not an outcome. */
            }
        }
    }

    /** Null when the pack loaded, or when the server never sent one. */
    public static String failure() {
        return failure;
    }

    public static void forget() {
        failure = null;
    }
}
