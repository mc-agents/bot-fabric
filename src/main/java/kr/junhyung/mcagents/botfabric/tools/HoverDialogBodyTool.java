package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.render.Cursor;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.dialog.DialogScreen;

import java.util.List;

/**
 * Hover a piece of a dialog's body and capture the frame with its tooltip drawn.
 *
 * <p>The cursor is put there through {@link Cursor}, as hover-slot does, so the frame's own
 * extraction takes the widget as hovered and the run under the cursor draws its hover event. The
 * lines go back beside the frame, built the way the frame builds them.
 *
 * <p>The frame is taken once the widget reports itself hovered, which happens while a frame is
 * extracted, so the frame the next tick reads has the tooltip in it. A point on no piece is given
 * a couple of ticks for a frame to pass and captured as it is.
 */
public final class HoverDialogBodyTool implements Tool {

    /** Two seconds: several frames even at the few per second a software rasteriser manages. */
    private static final int HOVER_TICKS = 40;

    private static final int BARE_TICKS = 2;

    private static final String NO_TOOLTIP = "shows no tooltip when hovered; click-dialog-body presses it";

    private final TaskScheduler scheduler;

    public HoverDialogBodyTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "hover-dialog-body";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        scheduler.submit(new HoverTask(parsed, parsed.integer("width", 854), parsed.integer("height", 480),
                parsed.bool("image", true)), call);
    }

    private static final class HoverTask implements Task {
        private enum Phase { WAITING, HOVERING, CAPTURING }

        private final Args args;
        private final int width;
        private final int height;
        private final boolean image;

        private DialogScreen<?> screen;
        private DialogBody.Target target;
        private boolean boosted;
        private Phase phase = Phase.WAITING;
        private int waiting;
        private int ticksHovering;
        private FrameGrab grab;
        private JsonObject data;

        private HoverTask(Args args, int width, int height, boolean image) {
            this.args = args;
            this.width = width;
            this.height = height;
            this.image = image;
        }

        @Override
        public String name() {
            return "hover-dialog-body";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayer();
            DialogBody.require();
            FrameBudget.boost();
            boosted = true;
        }

        @Override
        public boolean tick(CallContext call) throws Exception {
            if (phase == Phase.WAITING) {
                return await();
            }
            if (Mc.screen() != screen) {
                throw ToolException.refused("DIALOG_GONE", "the dialog closed or was replaced while hovering;"
                        + " read-dialog-body shows what is open now");
            }
            return switch (phase) {
                case WAITING -> false;
                case HOVERING -> hover(call);
                case CAPTURING -> capture(call);
            };
        }

        /** A frame drawn while the client loads shows the loading screen, so the hover starts once it is gone. */
        private boolean await() {
            if (Mouse.blocked()) {
                if (++waiting > Mouse.OVERLAY_TICKS) {
                    throw Mouse.stillLoading();
                }
                return false;
            }
            screen = DialogBody.require();
            target = DialogBody.target(DialogBody.read(screen), args,
                    DialogBody.Piece::hovers, style -> style.getHoverEvent() != null, NO_TOOLTIP);
            Cursor.set(target.point().x(), target.point().y());
            phase = Phase.HOVERING;
            return false;
        }

        private boolean hover(CallContext call) {
            DialogBody.Piece piece = target.piece();
            boolean hovered = piece == null ? ticksHovering >= BARE_TICKS : piece.widget().isHovered();
            if (!hovered && ++ticksHovering < (piece == null ? BARE_TICKS : HOVER_TICKS)) {
                return false;
            }

            data = new JsonObject();
            data.add("point", target.point().json());
            data.add("piece", DialogBody.json(piece));
            data.addProperty("hovered", piece != null && hovered);
            data.add("tooltip", piece != null && piece.hovers() ? DialogBody.lines(piece.tooltip()) : JsonNull.INSTANCE);

            if (!image) {
                data.add("frame", JsonNull.INSTANCE);
                call.ok("hovered " + what(), data);
                return true;
            }
            grab = FrameGrab.start("hover.png", width, height);
            phase = Phase.CAPTURING;
            return false;
        }

        private boolean capture(CallContext call) throws Exception {
            FrameGrab.Taken taken = grab.poll();
            if (taken == null) {
                return false;
            }
            data.add("frame", taken.size());
            call.ok("hovered " + what(), data, List.of(taken.blob()));
            return true;
        }

        private String what() {
            return target.piece() != null
                    ? target.piece().describe()
                    : "(" + target.point().x() + ", " + target.point().y() + ")";
        }

        @Override
        public void cleanup(CallContext call) {
            Cursor.clear();
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
