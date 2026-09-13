package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import net.minecraft.client.player.LocalPlayer;

/**
 * Get up off the death screen, and answer once the server has put the bot somewhere.
 *
 * <p>Pressing the button is one packet, and answering then would be answering before anything had
 * happened: the server replies with a new player and then with where to stand, and a caller that
 * asked for its position straight after was told where the body fell. So this waits for the player
 * to be replaced, the same signal a backend switch arrives by, and for the position after it.
 *
 * <p>Never done unasked. A server under test may be checking what happens on death -- the screen, a
 * kept inventory, where it sends a player back to -- and a bot that got up again by itself would
 * have walked through the one thing the check was looking at.
 *
 * <p>A bot that is not dead is a state and not a mistake, so it is told so rather than refused.
 */
public final class RespawnTool implements Tool {

    /** The position the server sends lands after the new player, and it is the one worth reporting. */
    private static final int SETTLE_TICKS = 20;

    private final TaskScheduler scheduler;

    public RespawnTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "respawn";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new RespawnTask(), call);
    }

    private static final class RespawnTask implements Task {
        private LocalPlayer dead;
        private int settling;

        @Override
        public String name() {
            return "respawn";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayerEvenIfDead();

            if (!Mc.dead(player)) {
                call.ok("The bot is not dead. It is at " + Positions.point(player.blockPosition()) + ".");
                return;
            }
            dead = player;
            player.respawn();
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer now = Mc.client().player;

            if (now == null || now == dead) {
                return false;
            }
            if (++settling < SETTLE_TICKS) {
                return false;
            }

            call.ok("Respawned at " + Positions.point(now.blockPosition())
                    + " in " + now.level().dimension().identifier().getPath() + ".");
            return true;
        }
    }
}
