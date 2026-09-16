package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;

/**
 * Wait a number of client ticks, in a world.
 *
 * <p>The wait ends with the connection, as the other kind of bot's does: a kick or a leave during
 * it answers NOT_IN_GAME on the tick the player goes, rather than counting on through the title
 * screen and reporting a wait the world never saw. A dead bot can still wait, and so can one the
 * proxy is moving between backends -- the player is gone for those ticks too, but the connection
 * is not, and the reconfiguration screen is what says so.
 */
public final class WaitTicksTool implements Tool {
    private final TaskScheduler scheduler;

    public WaitTicksTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "wait-ticks";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        int ticks = new Args(args).integer("ticks", -1);
        if (ticks < 1) {
            throw ToolException.badArgs("ticks must be at least 1");
        }
        scheduler.submit(new WaitTask(ticks), call);
    }

    private static final class WaitTask implements Task {
        private final int ticks;
        private int elapsed;

        private WaitTask(int ticks) {
            this.ticks = ticks;
        }

        @Override
        public String name() {
            return "wait-ticks";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayerEvenIfDead();
        }

        @Override
        public boolean tick(CallContext call) {
            if (Mc.client().player == null && !(Mc.screen() instanceof ServerReconfigScreen)) {
                throw ToolException.notInGame();
            }
            if (++elapsed < ticks) {
                return false;
            }
            call.ok("Waited " + ticks + " tick(s).");
            return true;
        }
    }
}
