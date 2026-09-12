package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click an entity, which is what opens an NPC's dialogue or a villager's trades.
 *
 * <p>The hit result is not optional: the client's interact sends where on the entity the click
 * landed, and dereferences it without checking. Aiming at the eyes is where a player looking at an
 * NPC would click.
 */
public final class InteractEntityTool implements Tool {

    private final TaskScheduler scheduler;

    public InteractEntityTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "interact-entity";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new InteractTask(new Args(args).string("name"),
                args.get("maxDistance").getAsDouble()), call);
    }

    private static final class InteractTask implements Task {
        private final String query;
        private final double maxDistance;
        private final Approach approach = new Approach();

        private Entity target;

        private InteractTask(String query, double maxDistance) {
            this.query = query;
            this.maxDistance = maxDistance;
        }

        @Override
        public String name() {
            return "interact-entity";
        }

        @Override
        public void start(CallContext call) {
            target = Entities.require(Mc.requirePlayer(), query, maxDistance);
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();

            if (!approach.reached(player, target)) {
                return false;
            }

            Vec3 eyes = target.getEyePosition();
            player.lookAt(EntityAnchorArgument.Anchor.EYES, eyes);
            Mc.client().gameMode.interact(player, target, new EntityHitResult(target, eyes),
                    InteractionHand.MAIN_HAND);
            player.swing(InteractionHand.MAIN_HAND);

            call.ok("Right-clicked " + Entities.named(target) + ".");
            return true;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
