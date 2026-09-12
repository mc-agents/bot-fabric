package kr.junhyung.mcagents.botfabric.render;

import java.util.concurrent.atomic.AtomicInteger;

public final class FrameBudget {
    private static int idleFps = 1;
    private static final int BUSY_FPS = 60;

    private static final AtomicInteger boosts = new AtomicInteger();

    private FrameBudget() {
    }

    /** What the operator asked for, which was sent and never read until now. */
    public static void idle(int fps) {
        idleFps = Math.clamp(fps, 1, 260);
    }

    public static int current() {
        return boosts.get() > 0 ? BUSY_FPS : idleFps;
    }

    public static void boost() {
        boosts.incrementAndGet();
    }

    public static void release() {
        boosts.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }
}
