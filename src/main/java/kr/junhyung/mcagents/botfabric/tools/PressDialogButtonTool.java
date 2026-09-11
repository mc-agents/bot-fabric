package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PressDialogButtonTool implements Tool {
    private static final int MAX_WIDGETS = 256;
    private static final int SETTLE_TICKS = 2;
    private static final int CONFIRM_TICKS = 60;

    private final TaskScheduler scheduler;

    public PressDialogButtonTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "press-dialog-button";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        scheduler.submit(new PressTask(new Args(args).string("label")), call);
    }

    static List<AbstractButton> buttonsOf(Screen screen) {
        List<AbstractButton> buttons = new ArrayList<>();
        collect(screen.children(), buttons);
        return buttons;
    }

    private static void collect(List<? extends GuiEventListener> children, List<AbstractButton> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractButton button) {
                out.add(button);
            }
            if (child instanceof ContainerEventHandler container && out.size() < MAX_WIDGETS) {
                collect(container.children(), out);
            }
        }
    }

    private static final class PressTask implements Task {
        private final String label;

        private String pressed;
        private boolean confirmed;
        private String confirmedWith;
        private String confirmTitle;
        private int ticksSincePress;

        private PressTask(String label) {
            this.label = label;
        }

        @Override
        public String name() {
            return "press-dialog-button";
        }

        @Override
        public boolean tick(CallContext call) {
            Screen screen = Mc.client().screen;
            if (pressed == null) {
                press(screen);
                return false;
            }

            ticksSincePress++;
            if (screen instanceof ConfirmScreen confirm) {
                return confirm(call, confirm);
            }
            if (ticksSincePress < SETTLE_TICKS) {
                return false;
            }
            report(call, screen);
            return true;
        }

        private void press(Screen screen) {
            if (screen == null) {
                throw ToolException.refused("NO_SCREEN", "no screen is open");
            }
            List<AbstractButton> buttons = buttonsOf(screen);
            AbstractButton match = pick(buttons, label);
            if (match == null) {
                throw ToolException.refused("NO_SUCH_BUTTON",
                        "no button matching \"" + label + "\" on " + screen.getClass().getSimpleName()
                                + "; it offers " + describe(buttons));
            }
            if (!match.isActive()) {
                throw ToolException.refused("BUTTON_DISABLED",
                        "the button \"" + match.getMessage().getString() + "\" is disabled");
            }
            pressed = match.getMessage().getString();
            match.onPress(new MouseButtonInfo(0, 0));
        }

        private boolean confirm(CallContext call, ConfirmScreen screen) {
            List<AbstractButton> buttons = buttonsOf(screen);
            AbstractButton accept = buttons.isEmpty() ? null : buttons.getFirst();
            if (accept == null) {
                throw ToolException.refused("NO_CONFIRM_BUTTON",
                        "the client asked to confirm \"" + pressed + "\" but offered no button");
            }
            if (!accept.isActive()) {
                if (ticksSincePress > CONFIRM_TICKS) {
                    throw ToolException.refused("CONFIRM_STUCK",
                            "the confirmation for \"" + pressed + "\" never became clickable");
                }
                return false;
            }
            confirmedWith = accept.getMessage().getString();
            confirmTitle = screen.getTitle().getString();
            accept.onPress(new MouseButtonInfo(0, 0));
            confirmed = true;
            report(call, Mc.client().screen);
            return true;
        }

        private void report(CallContext call, Screen after) {
            JsonObject data = new JsonObject();
            data.addProperty("label", pressed);
            data.addProperty("confirmed", confirmed);
            if (confirmed) {
                data.addProperty("confirmedWith", confirmedWith);
                data.addProperty("confirmTitle", confirmTitle);
            }
            data.addProperty("screenAfter", after == null ? null : after.getClass().getSimpleName());

            String text = confirmed
                    ? "pressed \"" + pressed + "\", then \"" + confirmedWith + "\" on " + confirmTitle
                    : "pressed \"" + pressed + "\"";
            call.ok(text, data);
        }

        private static String describe(List<AbstractButton> buttons) {
            if (buttons.isEmpty()) {
                return "none";
            }
            return buttons.stream()
                    .map(button -> "\"" + button.getMessage().getString() + "\"")
                    .collect(Collectors.joining(", "));
        }

        private static AbstractButton pick(List<AbstractButton> buttons, String label) {
            String wanted = label.toLowerCase(Locale.ROOT);
            for (AbstractButton button : buttons) {
                if (button.getMessage().getString().equalsIgnoreCase(label)) {
                    return button;
                }
            }
            for (AbstractButton button : buttons) {
                if (button.getMessage().getString().toLowerCase(Locale.ROOT).contains(wanted)) {
                    return button;
                }
            }
            return null;
        }
    }
}
