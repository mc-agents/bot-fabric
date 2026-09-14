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

/**
 * Swing at an entity, more than once when asked.
 *
 * <p>A task because of the gap between swings: the attack cooldown means two hits in one tick land
 * as one, and the ten ticks between them are bot-mineflayer's, so a mob takes the same beating from
 * either kind of bot.
 *
 * <p>A target that dies partway through is reported as the count that landed rather than as a
 * failure. Something that went away because it was killed is the tool working.
 */
public final class AttackEntityTool implements Tool {

    private static final int INTERVAL_TICKS = 10;

    private final TaskScheduler scheduler;

    public AttackEntityTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "attack-entity";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new AttackTask(Selector.of(args), new Args(args).integer("times", 1)), call);
    }

    private static final class AttackTask implements Task {
        private final Selector selector;
        private final int times;

        private final Approach approach = new Approach();

        private Entity target;
        private String label;
        private int landed;
        private int waiting;

        private AttackTask(Selector selector, int times) {
            this.selector = selector;
            this.times = times;
        }

        @Override
        public String name() {
            return "attack-entity";
        }

        @Override
        public void start(CallContext call) {
            target = selector.resolve(Mc.requirePlayer());
            label = Entities.label(target);
        }

        @Override
        public boolean tick(CallContext call) {
            if (waiting > 0) {
                waiting--;
                return false;
            }
            if (target.isRemoved()) {
                call.ok("Hit " + label + " " + landed + " time(s) out of " + times
                        + "; it left the world before the rest landed.");
                return true;
            }

            LocalPlayer player = Mc.requirePlayer();

            if (selector.crosshair()) {
                /* Not followed: a target picked by where the bot looks is hit only while it is there. */
                if (!(Mc.client().hitResult instanceof EntityHitResult hit) || hit.getEntity() != target) {
                    call.ok("Hit " + label + " " + landed + " time(s) out of " + times
                            + "; it left the crosshair before the rest landed.");
                    return true;
                }
            } else {
                /* Before each swing, not once: a mob that backs off is followed rather than missed. */
                if (!approach.reached(player, target)) {
                    return false;
                }
                player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            }

            Mc.client().gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
            landed++;

            if (landed == times) {
                call.ok("Hit " + label + " " + landed + " time(s).");
                return true;
            }
            waiting = INTERVAL_TICKS;
            return false;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
