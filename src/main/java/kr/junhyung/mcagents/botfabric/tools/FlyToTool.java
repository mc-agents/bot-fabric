package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Fly straight to a position, through anything in the way. Creative only.
 *
 * <p>What walking cannot do: get onto a roof, over a wall, across a hole. The straight line is the
 * point, so nothing is checked along it, and a destination inside a block leaves the bot inside that
 * block -- the same as a creative player flying into one.
 *
 * <p>Half a block a tick rather than one jump: the server watches how far a flying player moves
 * between packets, and a jump of several blocks is what a speed check is looking for.
 */
public final class FlyToTool implements Tool {

    private static final double PER_TICK = 0.5;
    private static final long TIMEOUT_MS = 20_000;

    private final TaskScheduler scheduler;

    public FlyToTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "fly-to";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new FlyTask(Positions.of(args)), call);
    }

    private static final class FlyTask implements Task {
        private final BlockPos target;
        private final long startedNanos = System.nanoTime();

        private FlyTask(BlockPos target) {
            this.target = target;
        }

        @Override
        public String name() {
            return "fly-to";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();
            String mode = Mc.client().gameMode.getPlayerMode().getName();

            if (!player.getAbilities().mayfly) {
                throw ToolException.refused("NOT_CREATIVE",
                        "fly-to needs creative mode, but the bot is in " + mode);
            }
            player.getAbilities().flying = true;
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();
            Vec3 destination = Vec3.atLowerCornerOf(target);
            Vec3 remaining = destination.subtract(player.position());

            if (remaining.length() <= PER_TICK) {
                player.setPos(destination);
                call.ok("Flew to " + Positions.point(target) + ".");
                return true;
            }
            if (elapsedMs() > TIMEOUT_MS) {
                throw ToolException.refused("FLIGHT_TIMEOUT", "flight timed out after "
                        + TIMEOUT_MS + "ms, the destination may be unreachable");
            }

            player.setPos(player.position().add(remaining.normalize().scale(PER_TICK)));
            player.setDeltaMovement(Vec3.ZERO);

            return false;
        }

        /**
         * Flight goes off at the end, the way bot-mineflayer's does. A bot left flying drifts, and
         * the next tool to look at where it is standing would be told something that keeps changing.
         */
        @Override
        public void cleanup(CallContext call) {
            LocalPlayer player = Mc.client().player;

            if (player != null) {
                player.getAbilities().flying = false;
            }
        }

        private long elapsedMs() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }
    }
}
