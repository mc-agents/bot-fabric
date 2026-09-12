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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Put the held block in an empty space.
 *
 * <p>A block is never placed at a position: it is placed against the face of a neighbour, and the
 * server works out where it lands. So the work here is finding a neighbour that exists, and the
 * caller's {@code faceDirection} says which to try first rather than which is the only one.
 *
 * <p>A space that already holds something is a state and not a refusal, the same as nothing to dig.
 */
public final class PlaceBlockTool implements Tool {

    private static final Direction[] FACES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final TaskScheduler scheduler;

    public PlaceBlockTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "place-block";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new PlaceTask(Positions.of(args),
                named(new Args(args).string("faceDirection"))), call);
    }

    private static Direction named(String name) {
        for (Direction face : FACES) {
            if (face.getName().equals(name.toLowerCase(Locale.ROOT))) {
                return face;
            }
        }
        throw ToolException.badArgs("unknown face " + name);
    }

    private static final class PlaceTask implements Task {
        private final BlockPos at;
        private final List<Direction> order;
        private final Approach approach = new Approach();

        private Direction chosen;
        private BlockPos against;

        private PlaceTask(BlockPos at, Direction preferred) {
            this.at = at;
            this.order = new ArrayList<>(List.of(preferred));
            for (Direction face : FACES) {
                if (face != preferred) {
                    order.add(face);
                }
            }
        }

        @Override
        public String name() {
            return "place-block";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            /* Standing in the space is the one placement a server will never accept. */
            BlockPos feet = player.blockPosition();
            if (at.equals(feet) || at.equals(feet.above())) {
                throw ToolException.refused("INSIDE_THE_BOT",
                        "Cannot place a block inside the bot itself");
            }

            if (!player.level().getBlockState(at).isAir()) {
                call.ok(Positions.point(at) + " already holds " + BuiltInRegistries.BLOCK
                        .getKey(player.level().getBlockState(at).getBlock()).getPath() + ".");
                return;
            }

            for (Direction face : order) {
                if (!player.level().getBlockState(at.relative(face)).isAir()) {
                    chosen = face;
                    against = at.relative(face);
                    return;
                }
            }

            throw ToolException.refused("NOTHING_TO_PLACE_AGAINST",
                    "No solid block next to " + Positions.point(at) + " to place against");
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (!approach.reached(player, against)) {
                return false;
            }
            if (player.getMainHandItem().isEmpty()) {
                throw ToolException.refused("EMPTY_HAND", "Nothing is in hand to place");
            }

            /* The neighbour's face that looks back at the empty space, and its middle. */
            Direction hit = chosen.getOpposite();
            Vec3 middle = Vec3.atCenterOf(against).add(hit.getUnitVec3().scale(0.5));

            player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
            Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(middle, hit, against, false));
            player.swing(InteractionHand.MAIN_HAND);

            call.ok("Placed a block at " + Positions.point(at)
                    + " against its " + chosen.getName() + " face.");
            return true;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
