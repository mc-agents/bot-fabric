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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Right-click with what the bot is holding, keeping the button down when asked.
 *
 * <p>A bow is the reason for the holding: the item has to stay in use for the draw to build up, and
 * a single use-and-release fires nothing. The client keeps using it only while the button is down,
 * so the task holds the button, uses the item once and lets go after the time asked for.
 */
public final class UseHeldItemTool implements Tool {

    private static final long TICK_MS = 50;

    private final TaskScheduler scheduler;

    public UseHeldItemTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "use-held-item";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new UseTask(parsed.bool("offhand", false), parsed.integer("holdMs", 0)), call);
    }

    /** The stack in the words bot-mineflayer uses: what a server called it, else what it is. */
    static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "an empty hand";
        }
        String name = stack.has(DataComponents.CUSTOM_NAME)
                ? stack.getHoverName().getString()
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        return name + " x" + stack.getCount();
    }

    /**
     * Through the game mode, which tells the server. The player's own release only stops the
     * client's copy of the use, and the server went on drawing a bow nobody would ever let go.
     * A useItem step of run-inputs lets go the same way.
     */
    static void release(LocalPlayer player) {
        UseKey.release();
        if (player != null && player.isUsingItem()) {
            Mc.client().gameMode.releaseUsingItem(player);
        }
    }

    private static final class UseTask implements Task {
        private final boolean offhand;
        private final int holdMs;

        private String held;
        private int remainingTicks;

        private UseTask(boolean offhand, int holdMs) {
            this.offhand = offhand;
            this.holdMs = holdMs;
        }

        @Override
        public String name() {
            return "use-held-item";
        }

        @Override
        public void start(CallContext call) {
            LocalPlayer player = Mc.requirePlayer();
            InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

            held = describe(player.getItemInHand(hand));
            remainingTicks = (int) Math.ceil(holdMs / (double) TICK_MS);

            if (holdMs == 0) {
                UseKey.holdUntilUsed();
            } else {
                UseKey.hold();
            }
            Mc.client().gameMode.useItem(player, hand);

            if (holdMs == 0) {
                call.ok("Used " + held + " in the " + where() + ".");
            }
        }

        @Override
        public boolean tick(CallContext call) {
            if (--remainingTicks > 0) {
                return false;
            }

            release(Mc.requirePlayer());
            call.ok("Used " + held + " in the " + where() + ", held for " + holdMs + "ms and released.");

            return true;
        }

        /**
         * A cancelled or expired hold must not leave the button down: the client would keep the item
         * in use for as long as the bot is in the world. Only a hold this task started is released
         * here, because a plain use leaves the item in use on purpose -- that is how food is eaten --
         * and {@link UseKey} lets go of that one when the eating is done.
         */
        @Override
        public void cleanup(CallContext call) {
            if (holdMs > 0) {
                release(Mc.client().player);
            }
        }

        private String where() {
            return offhand ? "off-hand" : "main hand";
        }
    }
}
