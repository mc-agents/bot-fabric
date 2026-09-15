package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;

/**
 * One step of run-inputs, as parsed: exactly one of press, click, command, wait or waitFor.
 *
 * <p>A step object arrives with all twelve fields, the ones outside its kind filled in with their
 * defaults, so which kind it is comes from which of the five naming fields is not null and never
 * from the rest. {@link #asked()} is the step in the words the answer names it by, so a step the
 * game refused or the timeout cut can still be said; the other kind of bot spells it the same.
 */
sealed interface Step permits Step.Press, Step.Click, Step.Command, Step.Wait, Step.WaitFor {

    List<String> KINDS = List.of("press", "click", "command", "wait", "waitFor");

    String kind();

    String asked();

    /** The ticks the step takes at the least, which is what TOO_LONG adds up before anything is sent. */
    int atLeastTicks();

    record Press(Key key, Integer slot, int holdTicks) implements Step {

        @Override
        public String kind() {
            return "press";
        }

        @Override
        public String asked() {
            String pressed = key == Key.HOTBAR ? "press hotbar " + slot : "press " + key.wire();
            return holdTicks > 1 ? pressed + " for " + holdTicks + " ticks" : pressed;
        }

        /** Down for holdTicks, then up for the one tick the game reads it up on. */
        @Override
        public int atLeastTicks() {
            return holdTicks + 1;
        }
    }

    record Click(int slot, String button, boolean shift, String mode, Integer hotbar) implements Step {

        @Override
        public String kind() {
            return "click";
        }

        @Override
        public String asked() {
            if (!"click".equals(mode)) {
                return mode + " slot " + slot + (hotbar == null ? "" : " with hotbar " + hotbar);
            }
            return "click slot " + slot + ("right".equals(button) ? " with the right button" : "")
                    + (shift ? " with shift" : "");
        }

        @Override
        public int atLeastTicks() {
            return 1;
        }

        /** The click, in click-slot's own wire names. */
        JsonObject args() {
            JsonObject args = new JsonObject();
            args.addProperty("slot", slot);
            args.addProperty("outside", false);
            args.addProperty("button", button);
            args.addProperty("shift", shift);
            args.addProperty("mode", mode);
            args.addProperty("hotbar", hotbar);
            return args;
        }
    }

    /** {@code text} carries exactly one leading slash, however the command was given. */
    record Command(String text) implements Step {

        @Override
        public String kind() {
            return "command";
        }

        @Override
        public String asked() {
            return "command " + text;
        }

        @Override
        public int atLeastTicks() {
            return 0;
        }
    }

    record Wait(int ticks) implements Step {

        @Override
        public String kind() {
            return "wait";
        }

        @Override
        public String asked() {
            return "wait " + ticks + (ticks == 1 ? " tick" : " ticks");
        }

        @Override
        public int atLeastTicks() {
            return ticks;
        }
    }

    record WaitFor(Watch watch) implements Step {

        @Override
        public String kind() {
            return "waitFor";
        }

        @Override
        public String asked() {
            return "wait for /" + watch.source() + "/ on " + watch.feed();
        }

        @Override
        public int atLeastTicks() {
            return 0;
        }
    }

    static List<Step> parse(JsonElement given) {
        if (given == null || !given.isJsonArray() || given.getAsJsonArray().isEmpty()) {
            throw ToolException.badArgs("expected a non-empty array of steps");
        }
        List<Step> steps = new ArrayList<>();
        int number = 0;
        for (JsonElement element : given.getAsJsonArray()) {
            number++;
            if (!element.isJsonObject()) {
                throw ToolException.badArgs("step " + number + " is not an object");
            }
            steps.add(parse(number, element.getAsJsonObject()));
        }
        return steps;
    }

    private static Step parse(int number, JsonObject step) {
        List<String> named = KINDS.stream().filter(kind -> given(step, kind)).toList();
        if (named.isEmpty()) {
            throw ToolException.refused("BAD_STEP",
                    "step " + number + " names none of press, click, command, wait or waitFor");
        }
        if (named.size() > 1) {
            throw ToolException.refused("BAD_STEP",
                    "step " + number + " names both " + named.get(0) + " and " + named.get(1));
        }

        Args args = new Args(step);
        try {
            return switch (named.get(0)) {
                case "press" -> {
                    Key key = Key.of(args.string("press"));
                    Integer slot = given(step, "slot") ? args.integer("slot", 0) : null;
                    if (key == Key.HOTBAR && slot == null) {
                        throw ToolException.refused("NO_SLOT", "hotbar needs slot");
                    }
                    yield new Press(key, key == Key.HOTBAR ? slot : null, args.integer("holdTicks", 1));
                }
                case "click" -> new Click(args.integer("click", 0), args.string("button", "left"),
                        args.bool("shift", false), args.string("mode", "click"),
                        given(step, "hotbar") ? args.integer("hotbar", 0) : null);
                case "command" -> {
                    String text = args.string("command");
                    yield new Command(text.startsWith("/") ? text : "/" + text);
                }
                case "wait" -> {
                    int ticks = args.integer("wait", 0);
                    if (ticks < 1) {
                        throw ToolException.badArgs("wait must be at least 1 tick");
                    }
                    yield new Wait(ticks);
                }
                default -> new WaitFor(Watch.of(args.string("feed", "actionBar"), args.string("waitFor")));
            };
        } catch (ToolException invalid) {
            /* Whatever the field at fault said, the step it is in comes first, as the other kind of bot words it. */
            throw new ToolException(invalid.errorClass(), invalid.code(),
                    "step " + number + ": " + invalid.getMessage(), invalid.retryable());
        }
    }

    private static boolean given(JsonObject step, String field) {
        JsonElement value = step.get(field);
        return value != null && !value.isJsonNull();
    }
}
