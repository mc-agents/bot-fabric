package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.ChatLines;
import kr.junhyung.mcagents.botfabric.mixin.ScreenInvoker;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Press something a server wrote in chat.
 *
 * <p>A quest's choices, a shop's items and half of a server's menus are chat lines with a click
 * event on them -- "[Accept]", "[Page 2]", a name that opens a dialog. The feed already carries
 * the component an agent reads that from; without this there is no way to answer one, and the
 * flows that a player drives by clicking could only be driven by guessing the command behind them.
 *
 * <p>The client's own handler does the pressing, and the in-game one: it runs a command or opens a
 * dialog and knows nothing of opening a URL or a file. A line from a server therefore cannot send
 * the host to a browser, because the method that would is not the method being called.
 */
public final class ClickChatTool implements Tool {

    /** A dialog opened by a click arrives a tick or two later, and the answer should mention it. */
    private static final int SETTLE_TICKS = 3;

    /** How long the client's own confirmation has to become clickable. */
    private static final int CONFIRM_TICKS = 60;

    /** Enough of what is clickable to tell a misspelling from a menu that never appeared. */
    private static final int MAX_LISTED = 8;

    private final TaskScheduler scheduler;

    public ClickChatTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "click-chat";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new ClickTask(new Args(args).string("match")), call);
    }

    /** One clickable run of a chat line: what it reads as, and what pressing it does. */
    private record Clickable(String text, ClickEvent event) {

        String describe() {
            return "\"" + text + "\" (" + action(event) + ")";
        }
    }

    private static String action(ClickEvent event) {
        return event.action().getSerializedName();
    }

    private static final class ClickTask implements Task {
        private final String match;

        private Clickable pressed;
        private int settling;
        private Confirmation.Accepted accepted;

        private ClickTask(String match) {
            this.match = match;
        }

        @Override
        public String name() {
            return "click-chat";
        }

        @Override
        public boolean tick(CallContext call) {
            if (pressed != null) {
                return settle(call);
            }

            List<Clickable> offered = clickable();
            Clickable found = pick(offered, match);

            if (found == null) {
                throw ToolException.refused("NO_SUCH_CHAT_CLICK",
                        "no chat line on screen has a clickable \"" + match + "\" on it. "
                                + describe(offered));
            }

            /*
            Only the two the client handles inside the game. Anything else is a click this bot has
            no business making on a host it does not own, and saying which one it was tells a
            caller whether the server meant something a player could press at all.
            */
            if (!isInGame(found.event())) {
                throw ToolException.refused("CLICK_NOT_IN_GAME", "\"" + found.text() + "\" is a "
                        + action(found.event()) + " click, and only run_command and show_dialog are"
                        + " pressed from here. The rest leave the game -- a browser, a file, the"
                        + " clipboard -- which is not this bot's to do.");
            }

            ScreenInvoker.mcagents$handleGameClick(found.event(), Mc.client(), Mc.screen());
            pressed = found;
            return false;
        }

        /**
         * What the click turned into. A command the client wants confirmed raises its own screen,
         * and leaving that up would put a modal in front of whatever the next call tried to do.
         */
        private boolean settle(CallContext call) {
            settling++;

            ConfirmScreen confirm = Confirmation.showing();

            if (confirm != null && accepted == null) {
                accepted = Confirmation.accept(confirm, pressed.text());

                if (accepted == null) {
                    if (settling > CONFIRM_TICKS) {
                        throw ToolException.refused("CONFIRM_STUCK",
                                "the confirmation for \"" + pressed.text() + "\" never became clickable");
                    }
                    return false;
                }
            }
            if (settling < SETTLE_TICKS) {
                return false;
            }

            String became = accepted != null
                    ? ", then " + accepted.describe()
                    : Mc.screen() == null ? "" : ", and " + Mc.screen().getClass().getSimpleName() + " opened";

            call.ok("clicked " + pressed.describe() + became + ".");
            return true;
        }

        private static boolean isInGame(ClickEvent event) {
            return event instanceof ClickEvent.RunCommand || event instanceof ClickEvent.ShowDialog;
        }

        /** Newest first, which is the order a line is looked for in. */
        private static List<Clickable> clickable() {
            List<Clickable> found = new ArrayList<>();

            for (Component line : ChatLines.recent()) {
                collect(line, found);
            }
            return found;
        }

        /**
         * Every run of a line that carries a click event, with the text that run reads as.
         *
         * <p>A run and not the whole line: "Quest: chop wood [Accept] [Decline]" is one line with
         * two of them, and a player clicks one of the two.
         */
        private static void collect(Component component, List<Clickable> into) {
            ClickEvent event = component.getStyle().getClickEvent();

            if (event != null) {
                into.add(new Clickable(component.getString(), event));
                return;
            }
            for (Component child : component.getSiblings()) {
                collect(child, into);
            }
        }

        private static Clickable pick(List<Clickable> offered, String match) {
            String wanted = match.toLowerCase(Locale.ROOT);

            for (Clickable one : offered) {
                if (one.text().equalsIgnoreCase(match)) {
                    return one;
                }
            }
            for (Clickable one : offered) {
                if (one.text().toLowerCase(Locale.ROOT).contains(wanted)) {
                    return one;
                }
            }
            return null;
        }

        private static String describe(List<Clickable> offered) {
            if (offered.isEmpty()) {
                return "Nothing said in chat since this bot joined is clickable at all.";
            }
            return "What is: " + offered.stream().limit(MAX_LISTED)
                    .map(Clickable::describe)
                    .collect(Collectors.joining(", "))
                    + (offered.size() > MAX_LISTED ? ", and " + (offered.size() - MAX_LISTED) + " more" : "")
                    + ".";
        }
    }
}
