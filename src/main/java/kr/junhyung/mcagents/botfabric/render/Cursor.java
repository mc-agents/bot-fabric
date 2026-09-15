package kr.junhyung.mcagents.botfabric.render;

/**
 * Where the frame thinks the cursor is, when a tool has put it somewhere.
 *
 * <p>A bot's mouse never moves, so nothing is ever hovered and no tooltip is ever drawn. The
 * override is read only where the GUI is extracted for a frame -- the mouse handler is not touched,
 * so there is nothing to put back -- and the screen does the rest exactly as it does for a person:
 * the slot under it is hovered, and the hovered slot's tooltip is drawn.
 *
 * <p>In GUI-scaled units, which is what the extraction reads.
 */
public final class Cursor {

    private record Point(int x, int y) {
    }

    private static volatile Point over;

    private Cursor() {
    }

    public static void set(int x, int y) {
        over = new Point(x, y);
    }

    public static void clear() {
        over = null;
    }

    public static double x(double real) {
        Point point = over;
        return point == null ? real : point.x();
    }

    public static double y(double real) {
        Point point = over;
        return point == null ? real : point.y();
    }
}
