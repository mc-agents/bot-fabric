package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DialogTags;

/**
 * The quick actions key, G unless rebound.
 *
 * <p>The client's keybinding handler does the opening, from the dialog tag the server sent, and
 * tells the server nothing. A server cannot see this key pressed, so a tag is the only thing it can
 * have put behind it, and with an empty one a press would do nothing and look like it had worked.
 */
public final class PressQuickActionsTool implements Tool {

    /** The handler reads the press on the next tick and opens the dialog there, with nothing to wait for. */
    private static final int SETTLE_TICKS = 3;

    private final TaskScheduler scheduler;

    public PressQuickActionsTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "press-quick-actions";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new QuickActionsTask(), call);
    }

    private static final class QuickActionsTask implements Task {
        private boolean pressed;
        private int waiting;
        private int settling;

        @Override
        public String name() {
            return "press-quick-actions";
        }

        @Override
        public boolean tick(CallContext call) {
            KeyMapping key = Mc.client().options.keyQuickActions;

            if (!pressed) {
                /* The handler does not run under the loading overlay either, so the press would sit unread. */
                if (Mouse.blocked()) {
                    if (++waiting > Mouse.OVERLAY_TICKS) {
                        throw Mouse.stillLoading();
                    }
                    return false;
                }
                PressTask.requireGameTakesKeys();
                requireQuickActions();
                key.setDown(true);
                KeyMapping.click(KeyMappingHelper.getBoundKeyOf(key));
                pressed = true;
                return false;
            }

            key.setDown(false);
            if (++settling < SETTLE_TICKS) {
                return false;
            }
            call.ok("pressed quick actions, and " + (Mc.screen() == null
                    ? "nothing opened."
                    : Mc.screen().getClass().getSimpleName() + " opened."));
            return true;
        }

        @Override
        public void cleanup(CallContext call) {
            Mc.client().options.keyQuickActions.setDown(false);
        }

        private static void requireQuickActions() {
            boolean declared = Mc.requirePlayer().connection.registryAccess().lookupOrThrow(Registries.DIALOG)
                    .get(DialogTags.QUICK_ACTIONS)
                    .filter(tag -> tag.size() > 0)
                    .isPresent();
            if (!declared) {
                throw ToolException.refused("NO_QUICK_ACTIONS", "this server put no dialog in its"
                        + " minecraft:quick_actions tag, so the key opens nothing.");
            }
        }
    }
}
