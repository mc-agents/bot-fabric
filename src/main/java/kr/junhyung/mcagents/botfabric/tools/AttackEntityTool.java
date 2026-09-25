package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Swing at an entity, more than once when asked.
 *
 * <p>A task because of the gap between swings: the attack cooldown means two hits in one tick land
 * as one, and the ten ticks between them are bot-mineflayer's, so a mob takes the same beating from
 * either kind of bot.
 *
 * <p>A hit is counted when the server says it landed, not when the swing was sent. On a loaded
 * runner a swing from the edge of reach, or at an entity the client still held a stale copy of, was
 * never registered by the server, and the answer said it had been. The server's word is the damage
 * event, which the client keeps as the hurt animation on its copy of the entity.
 *
 * <p>A target that dies partway through is reported as the count that landed rather than as a
 * failure. Something that went away because it was killed is the tool working.
 */
public final class AttackEntityTool implements Tool {

    private static final int INTERVAL_TICKS = 10;

    /**
     * How long a swing has to be answered. The damage event is a round trip behind the attack
     * packet, and on a loaded runner that round trip was seen to take several ticks.
     */
    private static final int ACK_TICKS = 10;

    /** Unanswered swings in a row before the target is called unreachable rather than slow. */
    private static final int MAX_MISSES = 3;

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
        private boolean swung;
        private int sinceSwing;
        private int hurtTimeSeen;
        private int missed;

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
            if (swung) {
                sinceSwing++;
                if (acknowledged()) {
                    swung = false;
                    missed = 0;
                    landed++;
                    if (landed == times) {
                        call.ok(struck(landed) + ".");
                        return true;
                    }
                    /* The cooldown runs from the swing, so the ticks spent waiting come off it. */
                    waiting = INTERVAL_TICKS - sinceSwing;
                    return false;
                }
                if (sinceSwing < ACK_TICKS) {
                    return false;
                }
                swung = false;
                if (++missed == MAX_MISSES) {
                    throw new ToolException(ToolError.TOOL, "SWING_MISSED", unregistered(), true);
                }
            }
            if (waiting > 0) {
                waiting--;
                return false;
            }
            if (gone()) {
                call.ok("Hit " + label + " " + landed + " time(s) out of " + times
                        + "; it left the world before the rest landed.");
                return true;
            }

            LocalPlayer player = Mc.requirePlayer();

            if (selector.crosshair()) {
                /* Not followed: a target picked by where the bot looks is hit only while it is there. */
                if (!(Mc.client().hitResult instanceof EntityHitResult hit) || hit.getEntity() != target) {
                    call.ok(struck(landed) + " out of " + times
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
            swung = true;
            sinceSwing = 0;
            hurtTimeSeen = target instanceof LivingEntity living ? living.hurtTime : 0;
            return false;
        }

        /**
         * What was done, said so that it cannot be read as more than it is.
         *
         * <p>A hit is only a hit when the server said so, and the server says nothing about a swing
         * at something that takes no damage: an interaction box, an item frame, an armour stand.
         * For those the swing is counted when its window passes, which says the swing happened and
         * nothing about whether it did anything. Calling that a hit is how ten swings that a plugin
         * silently refused came back as ten hits, and the refusal was looked for everywhere except
         * in this sentence.
         */
        private String struck(int count) {
            boolean answers = target instanceof LivingEntity && !(target instanceof ArmorStand);

            return answers
                    ? "Hit " + label + " " + count + " time(s)"
                    : "Swung at " + label + " " + count + " time(s); it takes no damage, so the server "
                            + "confirmed nothing -- read the chat for a plugin that refused it";
        }

        /**
         * Whether the server answered the swing in flight. Its answer is the damage event, which the
         * client turns into {@code hurtTime} jumping to ten and counting down: a rise since the last
         * look is a fresh hit, while a value still draining from the previous one is not, and that
         * previous one can still be draining when the next swing goes out.
         */
        private boolean acknowledged() {
            if (gone()) {
                return true;
            }
            if (target instanceof LivingEntity living && !(target instanceof ArmorStand)) {
                int hurtTime = living.hurtTime;
                boolean hurt = hurtTime > hurtTimeSeen;
                hurtTimeSeen = hurtTime;
                return hurt;
            }
            /*
            Nothing that is not living has a hurt timer, and the server says nothing back about a
            swing at an item frame or an interaction entity unless it goes away. An armor stand is
            living but is answered the same way: a survival hit on it is an entity event and no
            damage event, so its hurt timer never rises, and it only breaks on a second hit within
            five game ticks, which the gap between swings never produces. The window passing is
            taken as the swing being over -- which is not the same as it having landed, and the
            answer says so rather than calling it a hit.
            */
            return sinceSwing >= ACK_TICKS;
        }

        /**
         * Dying counts as gone: the server refuses damage to a mob whose health is out, so swings at
         * it would go unanswered, and the corpse clearing during one of them would read as a hit.
         */
        private boolean gone() {
            return target.isRemoved() || target instanceof LivingEntity living && living.isDeadOrDying();
        }

        private String unregistered() {
            String why = " after " + MAX_MISSES + " swings; it may be out of reach, invulnerable, or already gone";
            if (landed == 0) {
                return "the server registered no hit on " + label + why;
            }
            return struck(landed) + ", then the server registered no hit" + why;
        }

        @Override
        public void cleanup(CallContext call) {
            approach.stop();
        }
    }
}
