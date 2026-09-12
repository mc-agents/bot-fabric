package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.ChatWatch;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Send the bot to another backend through the proxy and wait until it is there.
 *
 * <p>A backend switch is a fresh login on the same connection: the client is handed a new level and
 * a new player, and the socket to the proxy never closes. So the player being replaced is what says
 * the bot arrived, and it is the same thing the other kind of bot watches for under another name.
 *
 * <p>The other outcome is the proxy answering in chat, and it arrives first when it happens. There
 * is no packet for "that server does not exist" -- a proxy says it the way it would say anything
 * else -- so the two are waited on together and whichever lands first decides.
 */
public final class SwitchServerTool implements Tool {

    private static final Pattern ALREADY_THERE = Pattern.compile("already connected", Pattern.CASE_INSENSITIVE);

    private static final Pattern REFUSED = Pattern.compile(
            "does ?n[o']t exist|unable to connect|no available server|not a valid server",
            Pattern.CASE_INSENSITIVE);

    /** The position the backend sends lands after the login, and it is the one worth reporting. */
    private static final int SETTLE_TICKS = 20;

    private final TaskScheduler scheduler;

    public SwitchServerTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "switch-server";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new SwitchTask(parsed.string("target"), parsed.integer("timeoutMs", 30_000)), call);
    }

    private static final class SwitchTask implements Task {
        private final String target;
        private final long timeoutMs;
        private final AtomicReference<String> answered = new AtomicReference<>();
        private final Consumer<String> ear = line -> {
            if (ALREADY_THERE.matcher(line).find() || REFUSED.matcher(line).find()) {
                answered.compareAndSet(null, line);
            }
        };

        private LocalPlayer before;
        private long sentAtNanos;
        private int settling;

        private SwitchTask(String target, long timeoutMs) {
            this.target = target;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "switch-server";
        }

        @Override
        public void start(CallContext call) {
            before = Mc.requirePlayer();

            ChatWatch.listen(ear);
            Mc.requireConnection().sendCommand("server " + target);
            sentAtNanos = System.nanoTime();
        }

        @Override
        public boolean tick(CallContext call) {
            LocalPlayer now = Mc.client().player;

            if (settling > 0) {
                return arrived(call, now);
            }

            String line = answered.get();
            if (line != null) {
                if (ALREADY_THERE.matcher(line).find()) {
                    call.ok("Already on \"" + target + "\" at " + Positions.point(where(now)) + ".");
                    return true;
                }
                throw ToolException.refused("SWITCH_REFUSED", "The proxy refused the switch: " + line);
            }

            /* A new player object is the login, and the login is the arrival. */
            if (now != null && now != before) {
                settling = 1;
                return false;
            }

            if ((System.nanoTime() - sentAtNanos) / 1_000_000L > timeoutMs) {
                throw ToolException.refused("SWITCH_TIMEOUT", "The bot did not arrive on \"" + target
                        + "\" within " + timeoutMs + "ms. Check read-chat for what the proxy said"
                        + " and get-bot-status for the connection state.");
            }
            return false;
        }

        private boolean arrived(CallContext call, LocalPlayer now) {
            if (now == null) {
                return false;
            }
            if (++settling < SETTLE_TICKS) {
                return false;
            }

            call.ok("Now on \"" + target + "\" at " + Positions.point(where(now))
                    + " in " + now.level().dimension().identifier().getPath() + ".");
            return true;
        }

        private static BlockPos where(LocalPlayer player) {
            return player == null ? BlockPos.ZERO : player.blockPosition();
        }

        @Override
        public void cleanup(CallContext call) {
            ChatWatch.forget(ear);
        }
    }
}
