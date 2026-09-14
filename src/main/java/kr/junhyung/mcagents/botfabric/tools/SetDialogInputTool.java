package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.Feeds;
import kr.junhyung.mcagents.botfabric.mixin.AbstractSliderButtonAccessor;
import kr.junhyung.mcagents.botfabric.mixin.DialogScreenAccessor;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Set a checkbox, a cycle or a slider on the dialog that is open, so the button pressed after it
 * sends what was chosen.
 *
 * <p>The value goes into the control on the screen and nowhere else. What an action sends --
 * {@code $(key)} in a {@code dynamic/run_command} template -- is read from those controls when the
 * button is pressed, so a value kept beside them would be a value the server never gets.
 *
 * <p>The controls carry no key: a checkbox knows its label and nothing of the input it was built
 * for. The dialog's inputs are added to the screen in the order the dialog lists them, one control
 * each, so the n-th checkbox on the screen is the n-th boolean input.
 */
public final class SetDialogInputTool implements Tool {

    /** Enough to walk a screen's widgets without following a cycle out of one. */
    private static final int MAX_WIDGETS = 256;

    @Override
    public String name() {
        return "set-dialog-input";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        String key = new Args(args).string("key");
        JsonElement value = args.get("value");

        Mc.immediate(call, () -> set(call, key, value));
    }

    private static void set(CallContext call, String key, JsonElement wanted) {
        if (!(Mc.screen() instanceof DialogScreen<?> screen)) {
            throw ToolException.refused("NO_DIALOG", "no dialog is open, so there is no input to set");
        }
        Dialog dialog = ((DialogScreenAccessor) screen).mcagents$dialog();
        List<Input> inputs = dialog.common().inputs();
        Map<String, AbstractWidget> controls = controls(screen, inputs);

        Input input = inputs.stream().filter(candidate -> candidate.key().equals(key)).findFirst()
                .orElseThrow(() -> ToolException.refused("NO_SUCH_INPUT", inputs.isEmpty()
                        ? "the dialog has no inputs"
                        : "the dialog has no input \"" + key + "\"; its inputs are "
                                + inputs.stream().map(Input::key).collect(Collectors.joining(", "))));
        AbstractWidget control = controls.get(key);

        JsonObject data = switch (input.control()) {
            case BooleanInput checkbox -> tick(key, checkbox, (Checkbox) control, wanted);
            case SingleOptionInput cycle -> pick(key, cycle, cycleOf(control), wanted);
            case NumberRangeInput slider -> slide(key, slider, (AbstractSliderButton) control, wanted);
            case TextInput ignored -> throw ToolException.refused("TEXT_INPUT",
                    "\"" + key + "\" is a text field; type into it with type-text");
            default -> throw ToolException.refused("UNSUPPORTED_INPUT",
                    "\"" + key + "\" is an input this bot does not know how to set");
        };

        /* The dialog again with what its inputs hold now, so a read of the feed tells what the button will send. */
        Feeds.dialogValues(dialog, values(inputs, controls));

        call.ok("set \"" + key + "\"", data);
    }

    private static JsonObject tick(String key, BooleanInput input, Checkbox box, JsonElement wanted) {
        if (!isBoolean(wanted)) {
            throw wrongType(key, "a checkbox", "true or false", wanted);
        }
        boolean previous = box.selected();
        if (previous != wanted.getAsBoolean()) {
            box.onPress(new MouseButtonInfo(0, 0));
        }
        return result(key, "boolean", input.label(), new JsonPrimitive(box.selected()), new JsonPrimitive(previous));
    }

    /** By the option's id, or by the text it is shown as when no id matches: the id is what the template sends. */
    private static JsonObject pick(String key, SingleOptionInput input, CycleButton<SingleOptionInput.Entry> cycle,
            JsonElement wanted) {
        if (!isString(wanted)) {
            throw wrongType(key, "a cycle", "the id or the text of one of its options", wanted);
        }
        String asked = wanted.getAsString();
        SingleOptionInput.Entry chosen = input.entries().stream()
                .filter(entry -> entry.id().equals(asked)).findFirst()
                .or(() -> input.entries().stream()
                        .filter(entry -> entry.displayOrDefault().getString().equalsIgnoreCase(asked)).findFirst())
                .orElseThrow(() -> ToolException.refused("NO_SUCH_OPTION", "\"" + key + "\" has no option \""
                        + asked + "\"; it offers " + input.entries().stream()
                                .map(entry -> entry.id() + " (\"" + entry.displayOrDefault().getString() + "\")")
                                .collect(Collectors.joining(", "))));

        String previous = cycle.getValue().id();
        cycle.setValue(chosen);

        JsonObject data = result(key, "single_option", input.label(), new JsonPrimitive(cycle.getValue().id()),
                new JsonPrimitive(previous));
        data.addProperty("display", cycle.getValue().displayOrDefault().getString());
        return data;
    }

    /** Inside the range and then wherever the slider's own steps put it, which is what the button sends. */
    private static JsonObject slide(String key, NumberRangeInput input, AbstractSliderButton slider, JsonElement wanted) {
        if (!isNumber(wanted)) {
            throw wrongType(key, "a slider", "a number", wanted);
        }
        NumberRangeInput.RangeInfo range = input.rangeInfo();
        float low = Math.min(range.start(), range.end());
        float high = Math.max(range.start(), range.end());
        float asked = wanted.getAsFloat();

        if (asked < low || asked > high) {
            throw ToolException.refused("OUT_OF_RANGE", "\"" + key + "\" goes from " + plain(range.start())
                    + " to " + plain(range.end()) + ", and " + plain(asked) + " is outside it");
        }

        AbstractSliderButtonAccessor handle = (AbstractSliderButtonAccessor) slider;
        float previous = range.computeScaledValue((float) handle.mcagents$value());
        handle.mcagents$setValue(range.start() == range.end() ? 0.5 : Mth.inverseLerp(asked, range.start(), range.end()));
        float now = range.computeScaledValue((float) handle.mcagents$value());

        JsonObject data = result(key, "number_range", input.label(), new JsonPrimitive(now), new JsonPrimitive(previous));
        if (now != asked) {
            data.addProperty("requested", asked);
        }
        return data;
    }

    private static JsonObject result(String key, String type, Component label, JsonElement value, JsonElement previous) {
        JsonObject data = new JsonObject();
        data.addProperty("key", key);
        data.addProperty("type", type);
        data.addProperty("label", label.getString());
        data.add("labelComponent", Segments.raw(label));
        data.add("value", value);
        data.add("previous", previous);
        data.add("display", JsonNull.INSTANCE);
        data.add("requested", JsonNull.INSTANCE);
        return data;
    }

    /** What every input holds now, for the dialog feed. */
    private static JsonObject values(List<Input> inputs, Map<String, AbstractWidget> controls) {
        JsonObject values = new JsonObject();

        for (Input input : inputs) {
            AbstractWidget control = controls.get(input.key());
            switch (input.control()) {
                case BooleanInput ignored when control instanceof Checkbox box ->
                        values.addProperty(input.key(), box.selected());
                case SingleOptionInput ignored when control instanceof CycleButton<?> cycle ->
                        values.addProperty(input.key(), ((SingleOptionInput.Entry) cycle.getValue()).id());
                case NumberRangeInput slider when control instanceof AbstractSliderButton handle ->
                        values.addProperty(input.key(), slider.rangeInfo().computeScaledValue(
                                (float) ((AbstractSliderButtonAccessor) handle).mcagents$value()));
                case TextInput ignored when control instanceof EditBox box -> values.addProperty(input.key(), box.getValue());
                case TextInput ignored when control instanceof MultiLineEditBox box ->
                        values.addProperty(input.key(), box.getValue());
                default -> {
                }
            }
        }
        return values;
    }

    /**
     * Each input's control, by the input's key.
     *
     * <p>Matched by kind and position: the n-th input of a kind is the n-th control of that kind in
     * the order the screen added them. A mismatch -- a dialog whose controls the screen did not build --
     * is refused rather than guessed at, because setting the wrong control sends the wrong value.
     */
    private static Map<String, AbstractWidget> controls(Screen screen, List<Input> inputs) {
        List<AbstractWidget> widgets = new ArrayList<>();
        collect(screen.children(), widgets);

        Map<Class<?>, Integer> taken = new HashMap<>();
        Map<String, AbstractWidget> controls = new HashMap<>();

        for (Input input : inputs) {
            Class<?> family = family(input.control());
            if (family == null) {
                continue;
            }
            int index = taken.merge(family, 1, Integer::sum) - 1;
            List<AbstractWidget> ofFamily = widgets.stream().filter(family::isInstance).toList();

            if (index >= ofFamily.size()) {
                throw ToolException.refused("CONTROLS_NOT_FOUND", "the dialog lists input \"" + input.key()
                        + "\" but the screen has no control for it");
            }
            controls.put(input.key(), ofFamily.get(index));
        }
        return controls;
    }

    private static Class<?> family(InputControl control) {
        return switch (control) {
            case BooleanInput ignored -> Checkbox.class;
            case SingleOptionInput ignored -> CycleButton.class;
            case NumberRangeInput ignored -> AbstractSliderButton.class;
            case TextInput input -> input.multiline().isPresent() ? MultiLineEditBox.class : EditBox.class;
            default -> null;
        };
    }

    private static void collect(List<? extends GuiEventListener> children, List<AbstractWidget> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) {
                out.add(widget);
            }
            if (child instanceof ContainerEventHandler container && out.size() < MAX_WIDGETS) {
                collect(container.children(), out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static CycleButton<SingleOptionInput.Entry> cycleOf(AbstractWidget control) {
        return (CycleButton<SingleOptionInput.Entry>) control;
    }

    private static ToolException wrongType(String key, String what, String takes, JsonElement got) {
        return ToolException.refused("WRONG_VALUE_TYPE", "\"" + key + "\" is " + what + " and takes " + takes + ", not " + got);
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isNumber(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    private static String plain(float value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Float.toString(value);
    }
}
