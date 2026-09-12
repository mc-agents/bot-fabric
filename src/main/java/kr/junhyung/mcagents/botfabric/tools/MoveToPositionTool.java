package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.nav.PathNavigator;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Walk to a position.
 *
 * <p>The route is searched over the blocks the client can see, so it goes round what is in the way.
 * A target it cannot reach is reported as how far it got rather than waited out, because a caller
 * who hears "stopped 6 blocks short" can teleport, and one who hears nothing for a minute cannot.
 */
public final class MoveToPositionTool implements Tool {

    private final TaskScheduler scheduler;

    public MoveToPositionTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "move-to-position";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new WalkTask(Positions.of(args), args.get("range").getAsDouble(),
                parsed.integer("timeoutMs", 60_000)), call);
    }

    private static final class WalkTask implements Task {
        private final BlockPos target;
        private final double range;
        private final long timeoutMs;
        private final PathNavigator navigator = new PathNavigator();
        private final long startedNanos = System.nanoTime();

        private WalkTask(BlockPos target, double range, long timeoutMs) {
            this.target = target;
            this.range = range;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "move-to-position";
        }

        @Override
        public boolean tick(CallContext call) {
            Vec3 destination = Vec3.atLowerCornerOf(target);

            if (navigator.step(Mc.requirePlayer(), destination, range)) {
                call.ok("Moved to within " + Numbers.plain(range) + " block(s) of "
                        + Positions.point(target) + ".");
                return true;
            }

            if (navigator.stuck(Mc.requirePlayer()) || elapsedMs() > timeoutMs) {
                throw ToolException.refused("UNREACHABLE", "could not reach "
                        + Positions.point(target) + " within " + timeoutMs + "ms; it stopped "
                        + Numbers.oneDecimal(Mc.requirePlayer().position().distanceTo(destination))
                        + " blocks away: " + navigator.trouble()
                        + ". Teleport with run-command when the way is not walkable.");
            }
            return false;
        }

        /** Whatever happened, the key must not stay down: the bot would walk off on its own. */
        @Override
        public void cleanup(CallContext call) {
            navigator.stop();
        }

        private long elapsedMs() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }
    }
}
