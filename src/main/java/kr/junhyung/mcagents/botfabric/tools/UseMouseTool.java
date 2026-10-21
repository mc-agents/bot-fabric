package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Move the mouse over whatever screen is open, press or scroll there, and capture the frame.
 *
 * <p>For the screens the other tools have no words for, and for a dialog whose body has to be
 * scrolled. Everything goes in through {@link Mouse}, so the screen hovers, scrolls and takes the
 * press as it does for a player. The cursor is left where it was put, as a player's is: a frame
 * taken later still shows what it rests on.
 *
 * <p>A screen has to be open. Without one a press is not a click on anything: the client grabs the
 * mouse and the button swings or uses what the bot holds, which is another tool's business.
 */
public final class UseMouseTool implements Tool {

    /** A press and its release, and a frame or two for the screen to answer. */
    private static final int SETTLE_TICKS = 3;

    private final TaskScheduler scheduler;

    public UseMouseTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "use-mouse";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        int x = parsed.integer("x", -1);
        int y = parsed.integer("y", -1);
        if (x < 0 || y < 0) {
            throw ToolException.badArgs("use-mouse needs x and y");
        }
        String action = parsed.string("action", "move");
        int scroll = parsed.integer("scroll", -1);
        if (action.equals("scroll") && scroll == 0) {
            throw ToolException.badArgs("a scroll of 0 notches turns nothing");
        }
        scheduler.submit(new MouseTask(x, y, action, scroll, parsed.integer("width", 854),
                parsed.integer("height", 480), parsed.bool("image", true)), call);
    }

    private static final class MouseTask implements Task {
        private final int x;
        private final int y;
        private final String action;
        private final int scroll;
        private final int width;
        private final int height;
        private final boolean image;

        private String screenBefore;
        private int held = -1;
        private boolean boosted;
        private boolean acted;
        private int waiting;
        private int settling;
        private FrameGrab grab;
        private JsonObject data;

        private MouseTask(int x, int y, String action, int scroll, int width, int height, boolean image) {
            this.x = x;
            this.y = y;
            this.action = action;
            this.scroll = scroll;
            this.width = width;
            this.height = height;
            this.image = image;
        }

        @Override
        public String name() {
            return "use-mouse";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayer();
            FrameBudget.boost();
            boosted = true;
        }

        /** Once the mouse can be used: the screen is the one open then, because a reload can replace it. */
        private void act() {
            Screen screen = Mc.screen();
            if (screen == null) {
                throw ToolException.refused("NO_SCREEN", "no screen is open, so there is nothing for the mouse to"
                        + " act on; a press here would swing or use what the bot holds");
            }
            int guiWidth = Mc.client().getWindow().getGuiScaledWidth();
            int guiHeight = Mc.client().getWindow().getGuiScaledHeight();
            if (x >= guiWidth || y >= guiHeight) {
                throw ToolException.badArgs("(" + x + ", " + y + ") is off the screen, which is "
                        + guiWidth + "x" + guiHeight + " in GUI units");
            }
            screenBefore = screen.getClass().getSimpleName();

            Mouse.moveTo(x, y);
            switch (action) {
                case "left" -> press(Mouse.LEFT);
                case "right" -> press(Mouse.RIGHT);
                case "scroll" -> Mouse.scroll(scroll);
                case "move" -> {
                }
                default -> throw ToolException.badArgs("unknown action " + action);
            }
        }

        private void press(int button) {
            Mouse.press(button);
            held = button;
        }

        @Override
        public boolean tick(CallContext call) throws Exception {
            if (!acted) {
                if (Mouse.blocked()) {
                    if (++waiting > Mouse.OVERLAY_TICKS) {
                        throw Mouse.stillLoading();
                    }
                    return false;
                }
                act();
                acted = true;
                return false;
            }
            if (held >= 0) {
                Mouse.release(held);
                held = -1;
            }
            if (grab != null) {
                return capture(call);
            }
            if (++settling < SETTLE_TICKS) {
                return false;
            }

            Screen after = Mc.screen();
            data = new JsonObject();
            JsonObject point = new JsonObject();
            point.addProperty("x", x);
            point.addProperty("y", y);
            data.add("point", point);
            data.addProperty("action", action);
            if (action.equals("scroll")) {
                data.addProperty("scroll", scroll);
            } else {
                data.add("scroll", JsonNull.INSTANCE);
            }
            data.addProperty("screenBefore", screenBefore);
            data.addProperty("screenAfter", after == null ? null : after.getClass().getSimpleName());

            if (!image) {
                data.add("frame", JsonNull.INSTANCE);
                call.ok(action + " at (" + x + ", " + y + ")", data);
                return true;
            }
            grab = FrameGrab.start("mouse.png", width, height);
            return false;
        }

        private boolean capture(CallContext call) throws Exception {
            FrameGrab.Taken taken = grab.poll();
            if (taken == null) {
                return false;
            }
            data.add("frame", taken.size());
            call.ok(action + " at (" + x + ", " + y + ")", data, List.of(taken.blob()));
            return true;
        }

        @Override
        public void cleanup(CallContext call) {
            if (held >= 0) {
                Mouse.release(held);
                held = -1;
            }
            if (boosted) {
                FrameBudget.release();
                boosted = false;
            }
            if (grab != null) {
                grab.close();
            }
        }
    }
}
