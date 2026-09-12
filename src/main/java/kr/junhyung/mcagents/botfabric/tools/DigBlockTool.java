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
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

/**
 * Break a block, holding the button down until it gives.
 *
 * <p>Breaking is not one packet. The client tells the server it has started, keeps telling it the
 * block is still being hit, and the server decides when the block is gone -- in creative the first
 * of those is enough, in survival it takes as long as the tool in hand takes. Both look the same
 * from here: keep going until the block is no longer there.
 *
 * <p>Nothing to dig is a state, not a refusal: asking twice should not fail the second time.
 */
public final class DigBlockTool implements Tool {

    /** Long enough for a hand on stone, short enough to say so rather than sit on the deadline. */
    private static final int PATIENCE_TICKS = 600;

    private final TaskScheduler scheduler;

    public DigBlockTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "dig-block";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new DigTask(Positions.of(args)), call);
    }

    private static final class DigTask implements Task {
        private final BlockPos at;
        private final Approach approach = new Approach();

        private String block;
        private boolean started;
        private int ticks;

        private DigTask(BlockPos at) {
            this.at = at;
        }

        @Override
        public String name() {
            return "dig-block";
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (block == null) {
                if (player.level().getBlockState(at).isAir()) {
                    call.ok("Nothing to dig at " + Positions.point(at) + ".");
                    return true;
                }
                block = BuiltInRegistries.BLOCK
                        .getKey(player.level().getBlockState(at).getBlock()).getPath();
            }

            if (!approach.reached(player, at)) {
                return false;
            }

            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(at));

            if (!started) {
                started = true;
                Mc.client().gameMode.startDestroyBlock(at, Direction.UP);
            } else {
                Mc.client().gameMode.continueDestroyBlock(at, Direction.UP);
            }

            if (player.level().getBlockState(at).isAir()) {
                call.ok("Dug " + block + " at " + Positions.point(at) + ".");
                return true;
            }
            if (++ticks > PATIENCE_TICKS) {
                throw ToolException.refused("DIG_STUCK", block + " at " + Positions.point(at)
                        + " did not break in " + (PATIENCE_TICKS / 20) + "s."
                        + " Nothing in hand may be able to break it.");
            }
            return false;
        }

        /** A half-dug block left mid-swing keeps the client sending progress for a block it forgot. */
        @Override
        public void cleanup(CallContext call) {
            approach.stop();
            if (started && Mc.client().player != null) {
                Mc.client().gameMode.stopDestroyBlock();
            }
        }
    }
}
