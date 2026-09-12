package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.nav.Steering;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.world.entity.player.Input;

/**
 * Hold one movement key for a while, for when walking to a position is not what is wanted: stepping
 * onto a pressure plate, edging off a ledge, pushing against a block a plugin watches.
 */
public final class MoveInDirectionTool implements Tool {

    private static final long TICK_MS = 50;

    private final TaskScheduler scheduler;

    public MoveInDirectionTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "move-in-direction";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        String direction = parsed.string("direction");

        scheduler.submit(new HoldTask(direction, keys(direction),
                parsed.integer("durationMs", 1000)), call);
    }

    private static Input keys(String direction) {
        return switch (direction) {
            case "forward" -> new Input(true, false, false, false, false, false, false);
            case "back" -> new Input(false, true, false, false, false, false, false);
            case "left" -> new Input(false, false, true, false, false, false, false);
            case "right" -> new Input(false, false, false, true, false, false, false);
            default -> throw ToolException.badArgs("unknown direction " + direction);
        };
    }

    private static final class HoldTask implements Task {
        private final String direction;
        private final Input keys;
        private final int durationMs;

        private int remainingTicks;

        private HoldTask(String direction, Input keys, int durationMs) {
            this.direction = direction;
            this.keys = keys;
            this.durationMs = durationMs;
            this.remainingTicks = (int) Math.ceil(durationMs / (double) TICK_MS);
        }

        @Override
        public String name() {
            return "move-in-direction";
        }

        @Override
        public boolean tick(CallContext call) {
            Steering.press(keys);

            if (--remainingTicks > 0) {
                return false;
            }

            Steering.release();
            call.ok("Moved " + direction + " for " + durationMs + "ms.");

            return true;
        }

        /** A cancelled hold that left the key down would walk the bot away for good. */
        @Override
        public void cleanup(CallContext call) {
            Steering.release();
        }
    }
}
