package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen;

/**
 * Press a piece of a dialog's body with the mouse.
 *
 * <p>The cursor is put on the piece and the left button pressed and released through the client's
 * mouse handler, so the screen picks the widget, the widget's hit test picks the run of text, and
 * the dialog sends what the run's click event says -- the path a player's click takes, where a
 * press that lands nowhere is a finding rather than something to work around.
 *
 * <p>What the press led to is watched for {@link #SETTLE_TICKS}: a dialog the server sent in its
 * place ends the wait at once, and anything else is reported as it stands at the end. A server
 * answering a click usually closes the dialog and sends the next one, so a dialog that closed is
 * given the whole wait to come back.
 */
public final class ClickDialogBodyTool implements Tool {

    private static final int SETTLE_TICKS = 40;

    /** How long the client's own confirmation has to become clickable. */
    private static final int CONFIRM_TICKS = 60;

    private static final String NO_CLICK = "carries no click event; hover-dialog-body shows what hovering it does";

    private final TaskScheduler scheduler;

    public ClickDialogBodyTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "click-dialog-body";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new ClickTask(new Args(args)), call);
    }

    private static final class ClickTask implements Task {
        private enum Phase { WAITING, RELEASING, SETTLING }

        private final Args args;

        private DialogScreen<?> pressed;
        private DialogBody.Target target;
        private Phase phase = Phase.WAITING;
        private boolean held;
        private boolean boosted;
        private int waiting;
        private int settling;
        private Confirmation.Accepted accepted;

        private ClickTask(Args args) {
            this.args = args;
        }

        @Override
        public String name() {
            return "click-dialog-body";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayer();
            DialogBody.require();
            FrameBudget.boost();
            boosted = true;
        }

        @Override
        public boolean tick(CallContext call) {
            return switch (phase) {
                case WAITING -> await();
                case RELEASING -> release();
                case SETTLING -> settle(call);
            };
        }

        /** The body is read once the mouse can be used, because a reload that held it can move the body too. */
        private boolean await() {
            if (Mouse.blocked()) {
                if (++waiting > Mouse.OVERLAY_TICKS) {
                    throw Mouse.stillLoading();
                }
                return false;
            }
            pressed = DialogBody.require();
            target = DialogBody.target(DialogBody.read(pressed), args,
                    piece -> piece.click() != null, style -> style.getClickEvent() != null, NO_CLICK);

            DialogBody.Piece piece = target.piece();
            if (piece != null && DialogBody.leavesTheGame(piece.click())) {
                throw ToolException.refused("CLICK_NOT_IN_GAME", piece.describe() + " is a "
                        + piece.click().action().getSerializedName() + " click, which leaves the game -- a"
                        + " browser, a file, the clipboard -- and is not this bot's to press.");
            }

            Mouse.moveTo(target.point().x(), target.point().y());
            Mouse.press(Mouse.LEFT);
            held = true;
            phase = Phase.RELEASING;
            return false;
        }

        private boolean release() {
            Mouse.release(Mouse.LEFT);
            held = false;
            phase = Phase.SETTLING;
            return false;
        }

        private boolean settle(CallContext call) {
            settling++;
            Screen now = Mc.screen();

            if (now instanceof ConfirmScreen confirm && accepted == null) {
                accepted = Confirmation.accept(confirm, what());
                if (accepted == null && settling > CONFIRM_TICKS) {
                    throw ToolException.refused("CONFIRM_STUCK",
                            "the confirmation for " + what() + " never became clickable");
                }
                return false;
            }
            if (now instanceof DialogScreen<?> && now != pressed) {
                report(call, "replaced", now);
                return true;
            }
            if (settling < SETTLE_TICKS) {
                return false;
            }

            String outcome;
            if (now == pressed) {
                outcome = "unchanged";
            } else if (now == null) {
                outcome = "closed";
            } else if (now instanceof WaitingForResponseScreen) {
                outcome = "waiting";
            } else {
                outcome = "other";
            }
            report(call, outcome, now);
            return true;
        }

        private void report(CallContext call, String outcome, Screen after) {
            JsonObject data = new JsonObject();
            data.add("point", target.point().json());
            data.add("piece", DialogBody.json(target.piece()));
            data.addProperty("outcome", outcome);
            data.addProperty("screenAfter", after == null ? null : after.getClass().getSimpleName());

            if (after instanceof DialogScreen<?> dialog && after != pressed) {
                JsonObject next = new JsonObject();
                next.addProperty("title", dialog.getTitle().getString());
                next.add("titleComponent", Segments.raw(dialog.getTitle()));
                data.add("dialogAfter", next);
            } else {
                data.add("dialogAfter", JsonNull.INSTANCE);
            }

            if (accepted != null) {
                JsonObject confirmed = new JsonObject();
                confirmed.addProperty("button", accepted.button());
                confirmed.addProperty("title", accepted.title());
                data.add("confirmed", confirmed);
            } else {
                data.add("confirmed", JsonNull.INSTANCE);
            }

            call.ok("pressed " + what() + ": " + outcome, data);
        }

        private String what() {
            return target.piece() != null
                    ? target.piece().describe()
                    : "(" + target.point().x() + ", " + target.point().y() + ")";
        }

        @Override
        public void cleanup(CallContext call) {
            if (held) {
                Mouse.release(Mouse.LEFT);
                held = false;
            }
            if (boosted) {
                FrameBudget.release();
                boosted = false;
            }
        }
    }
}
