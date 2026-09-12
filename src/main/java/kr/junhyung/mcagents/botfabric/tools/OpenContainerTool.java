package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Open the chest-like block at a position and answer with what it holds.
 *
 * <p>Two ticks at least: the click goes out as a packet and the server sends the window back, so
 * the screen the answer describes does not exist yet when the click is made. Waiting for it is the
 * whole reason this is a task rather than a read.
 *
 * <p>Which blocks count is a list rather than a test on the block, and it is the same list
 * bot-mineflayer carries, so the two kinds refuse the same coordinates with the same sentence. A
 * block that opens a menu without being a container -- most of what a plugin adds -- is
 * activate-block followed by wait-for-window, which is the general form of this tool.
 */
public final class OpenContainerTool implements Tool {

    private static final Set<String> CONTAINER_BLOCKS = Set.of(
            "chest", "trapped_chest", "ender_chest", "barrel", "hopper", "dispenser", "dropper",
            "shulker_box");

    /** The same reach bot-mineflayer applies, measured the same way: feet to block corner. */
    private static final double REACH = 3.0;

    private final TaskScheduler scheduler;

    public OpenContainerTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "open-container";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new OpenTask(Positions.of(args)), call);
    }

    private static boolean isContainer(String name) {
        return CONTAINER_BLOCKS.contains(name) || name.endsWith("_shulker_box");
    }

    private static final class OpenTask implements Task {
        private final BlockPos at;
        private boolean clicked;
        private int ticksSinceClick;

        private OpenTask(BlockPos at) {
            this.at = at;
        }

        @Override
        public String name() {
            return "open-container";
        }

        @Override
        public boolean tick(CallContext call) {
            if (!clicked) {
                click();
                clicked = true;
                return false;
            }

            AbstractContainerScreen<?> container = Windows.open();
            if (container != null) {
                JsonObject data = Windows.describe(container);
                call.ok("window \"" + container.getTitle().getString() + "\"", data);
                return true;
            }

            /*
            Nothing opening is a refusal and not a state: the caller named a container and asked for
            what is in it, so an answer describing no window would be an answer to another question.
            The deadline on the call is 30s; giving up earlier says which step failed.
            */
            if (++ticksSinceClick > 100) {
                throw ToolException.refused("NO_WINDOW_OPENED",
                        "the container at " + point() + " was clicked but no window opened within 5s");
            }
            return false;
        }

        private void click() {
            LocalPlayer player = Mc.requirePlayer();
            String block = BuiltInRegistries.BLOCK
                    .getKey(player.level().getBlockState(at).getBlock()).getPath();

            if (!isContainer(block)) {
                throw ToolException.refused("NOT_A_CONTAINER",
                        point() + " holds " + block + ", not a container");
            }

            Vec3 middle = Vec3.atCenterOf(at);
            double distance = player.position().distanceTo(Vec3.atLowerCornerOf(at));
            if (distance > REACH) {
                throw ToolException.refused("OUT_OF_REACH", point() + " is "
                        + Math.round(distance) + " blocks away and this kind of bot cannot walk to it yet."
                        + " Teleport to it with run-command first.");
            }

            player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
            Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(middle, Direction.UP, at, false));
            player.swing(InteractionHand.MAIN_HAND);
        }

        private String point() {
            return "(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
        }
    }
}
