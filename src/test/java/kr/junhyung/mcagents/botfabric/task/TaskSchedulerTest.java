package kr.junhyung.mcagents.botfabric.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kr.junhyung.mcagents.botfabric.BotConfig;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import org.junit.jupiter.api.Test;

/**
 * The scheduler's list belongs to the client thread. What this holds is that no other thread
 * reaches it, because the one that did took the whole client down.
 */
class TaskSchedulerTest {

    /* Never started, so a result goes to its outbound queue and no socket is involved. */
    private static final RpcClient OFFLINE =
            new RpcClient(new BotConfig("127.0.0.1", 8765, "test", 2000, false, 0, 6, 1));

    private static CallContext call() {
        return new CallContext(OFFLINE, "1", "test", 1_000);
    }

    /** Never finishes on its own, so only an abort settles it. */
    private static final class ForeverTask implements Task {
        @Override
        public String name() {
            return "forever";
        }

        @Override
        public boolean tick(CallContext context) {
            return false;
        }
    }

    /*
    abortAll used to drain into the running list itself. Called from the RPC reader while the
    render thread was iterating -- which is exactly what a dropped link does -- it threw
    ConcurrentModificationException out of Minecraft's tick and crashed the game.
    */
    @Test
    void abortingFromAnotherThreadDoesNotDisturbATickInProgress() throws Exception {
        TaskScheduler scheduler = new TaskScheduler();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch ticking = new CountDownLatch(1);

        for (int i = 0; i < 200; i++) {
            scheduler.submit(new ForeverTask(), call());
        }

        Thread client = new Thread(() -> {
            try {
                for (int tick = 0; tick < 2_000; tick++) {
                    scheduler.tick();
                    ticking.countDown();
                }
            } catch (Throwable t) {
                thrown.set(t);
            }
        });

        client.start();
        assertTrue(ticking.await(5, TimeUnit.SECONDS), "the client thread never ticked");

        for (int i = 0; i < 200; i++) {
            scheduler.abortAll("the link to mcp-server closed");
            scheduler.submit(new ForeverTask(), call());
        }

        client.join(10_000);

        assertEquals(null, thrown.get(), () -> "the tick threw: " + thrown.get());
    }

    @Test
    void anAbortSettlesEverythingInFlight() {
        TaskScheduler scheduler = new TaskScheduler();
        CallContext call = call();

        scheduler.submit(new ForeverTask(), call);
        scheduler.tick();
        assertEquals(1, scheduler.size());

        scheduler.abortAll("the link to mcp-server closed");
        scheduler.tick();

        assertEquals(0, scheduler.size());
        assertTrue(call.settled(), "a task abandoned by a dropped link still owes exactly one result");
    }
}
