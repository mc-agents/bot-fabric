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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click a block: a button, a lever, a door, or whatever a plugin put there.
 *
 * <p>What opens a window is this followed by wait-for-window, and that pair is the general form of
 * open-container. Nothing is waited for here, because a lever has no window and waiting for one
 * would make every button press cost a timeout.
 */
public final class ActivateBlockTool implements Tool {

    private final TaskScheduler scheduler;

    public ActivateBlockTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "activate-block";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new ActivateTask(Positions.of(args)), call);
    }

    private static final class ActivateTask implements Task {
        private final BlockPos at;
        private final Approach approach = new Approach();

        private ActivateTask(BlockPos at) {
            this.at = at;
        }

        @Override
        public String name() {
            return "activate-block";
        }

        @Override
        public void start(CallContext call) {
            if (!Mc.requirePlayer().level().isLoaded(at)) {
                throw ToolException.refused("NOT_LOADED", Positions.point(at)
                        + " is outside the loaded chunks, so there is no block to activate");
            }
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (!approach.reached(player, at)) {
                return false;
            }

            String block = BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(at).getBlock()).getPath();
            Vec3 middle = Vec3.atCenterOf(at);

            player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
            Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(middle, Direction.UP, at, false));
            player.swing(InteractionHand.MAIN_HAND);

            call.ok("Right-clicked " + block + " at " + Positions.point(at) + ".");
            return true;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
