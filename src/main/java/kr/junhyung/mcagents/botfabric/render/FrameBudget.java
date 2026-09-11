package kr.junhyung.mcagents.botfabric.render;

import java.util.concurrent.atomic.AtomicInteger;

public final class FrameBudget {
    private static final int IDLE_FPS = 1;
    private static final int BUSY_FPS = 60;

    private static final AtomicInteger boosts = new AtomicInteger();

    private FrameBudget() {
    }

    public static int current() {
        return boosts.get() > 0 ? BUSY_FPS : IDLE_FPS;
    }

    public static void boost() {
        boosts.incrementAndGet();
    }

    public static void release() {
        boosts.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }
}
