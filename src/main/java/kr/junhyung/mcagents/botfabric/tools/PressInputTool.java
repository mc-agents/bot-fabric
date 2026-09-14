package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.FeedWatch;
import kr.junhyung.mcagents.botfabric.nav.Steering;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.world.entity.player.Inventory;

/**
 * A key pressed tick by tick, the way the keyboard and the mouse press it.
 *
 * <p>Nothing here sends a packet or moves the player itself. jump, sneak and sprint are laid over the
 * key presses through {@link Steering}, where set-stance holds its crouch, and the rest are the
 * client's own key mappings, clicked and held down so its keybinding handler does what it does for a
 * person: starts using an item, swings, selects a slot, drops a stack. What the server receives is
 * therefore what a player's input would have made, which is the whole point for a game driven by it.
 *
 * <p>A press is made at the end of a tick and read by the next one. One made from {@link FeedWatch}
 * lands earlier: a packet is handled on the client thread before the tick runs, so a press made
 * there is read by the very tick the line arrived on.
 */
public final class PressInputTool implements Tool {

    private static final long TICK_MS = 50;

    private final TaskScheduler scheduler;

    public PressInputTool(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String name() {
        return "press-input";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Args parsed = new Args(args);
        Key key = Key.of(parsed.string("key"));
        Integer slot = args.get("slot") == null || args.get("slot").isJsonNull() ? null : parsed.integer("slot", 0);

        if (key == Key.HOTBAR && slot == null) {
            throw ToolException.refused("NO_SLOT", "hotbar needs slot, 0 being the leftmost hotbar slot.");
        }

        scheduler.submit(new PressTask(key, key == Key.HOTBAR ? slot : null,
                parsed.integer("holdTicks", 1), parsed.integer("repeat", 1), parsed.integer("intervalTicks", 1),
                Watch.of(args.get("after")), Watch.of(args.get("until")), parsed.integer("timeoutMs", 10_000)), call);
    }

    private enum Key {
        JUMP, SNEAK, SPRINT, USE, ATTACK, HOTBAR, SCROLL_UP, SCROLL_DOWN, SWAP_OFFHAND, DROP;

        static Key of(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException unknown) {
                throw ToolException.badArgs("unknown key " + name);
            }
        }

        String wire() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private record Watch(String feed, String source, Pattern pattern) {

        static Watch of(JsonElement given) {
            if (given == null || given.isJsonNull()) {
                return null;
            }
            Args args = new Args(given.getAsJsonObject());
            String source = args.string("pattern");
            try {
                return new Watch(args.string("feed"), source, Pattern.compile(source));
            } catch (PatternSyntaxException invalid) {
                throw ToolException.refused("BAD_PATTERN",
                        "\"" + source + "\" is not a valid regular expression: " + invalid.getDescription());
            }
        }

        boolean matches(String kind, String text) {
            return feed.equals(kind) && pattern.matcher(text).find();
        }

        JsonObject describe(String matched) {
            JsonObject watched = new JsonObject();
            watched.addProperty("feed", feed);
            watched.addProperty("pattern", source);
            watched.addProperty("matched", matched);
            return watched;
        }
    }

    private static final class PressTask implements Task {
        private enum Phase { WAITING, DOWN, UP, DONE }

        private final Key key;
        private final Integer slot;
        private final int holdTicks;
        private final int repeat;
        private final int intervalTicks;
        private final Watch after;
        private final Watch until;
        private final int timeoutMs;
        private final BiConsumer<String, String> listener = this::saw;

        private Phase phase;
        private int ticksLeft;
        private int presses;
        private boolean down;
        private long startedNanos;
        private long waitedMs;
        private String afterMatched;
        private String untilMatched;
        private String stopped = "done";

        private PressTask(Key key, Integer slot, int holdTicks, int repeat, int intervalTicks,
                Watch after, Watch until, int timeoutMs) {
            this.key = key;
            this.slot = slot;
            this.holdTicks = holdTicks;
            this.repeat = repeat;
            this.intervalTicks = intervalTicks;
            this.after = after;
            this.until = until;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String name() {
            return "press-input";
        }

        @Override
        public void start(CallContext call) {
            Mc.requirePlayer();
            /* The keybinding handler does not run under a screen: the keys go to the screen instead. */
            if (Mc.screen() != null) {
                throw ToolException.refused("WINDOW_OPEN",
                        "a window is open, and keys go to it rather than to the game; close-window first.");
            }
            long sequenceMs = ((long) repeat * holdTicks + (long) (repeat - 1) * intervalTicks) * TICK_MS;
            if (sequenceMs > timeoutMs) {
                throw ToolException.refused("TOO_LONG", repeat + " presses of " + ticks(holdTicks) + ", "
                        + ticks(intervalTicks) + " apart, take " + sequenceMs + "ms, longer than timeoutMs ("
                        + timeoutMs + "ms).");
            }

            startedNanos = System.nanoTime();
            /* The first press waits for the end of this tick, so the tick after reads it as down. */
            phase = after == null ? Phase.UP : Phase.WAITING;
            ticksLeft = 1;
            FeedWatch.listen(listener);
        }

        /** A feed line, on the client thread, before the tick that reads the keys. */
        private void saw(String kind, String text) {
            if (phase == Phase.WAITING && after.matches(kind, text)) {
                waitedMs = elapsedMs();
                afterMatched = text;
                press();
            } else if (until != null && (phase == Phase.DOWN || phase == Phase.UP) && until.matches(kind, text)) {
                untilMatched = text;
                stopped = "until";
                letGo();
                phase = Phase.DONE;
            }
        }

        @Override
        public boolean tick(CallContext call) {
            Mc.requirePlayer();

            if (phase == Phase.DONE) {
                return answer(call);
            }
            if (elapsedMs() > timeoutMs) {
                if (phase == Phase.WAITING) {
                    throw ToolException.refused("NO_MATCH", "nothing on the " + after.feed() + " feed matched /"
                            + after.source() + "/ within " + timeoutMs + "ms, so " + key.wire() + " was never pressed.");
                }
                letGo();
                stopped = "timeout";
                return answer(call);
            }

            switch (phase) {
                case DOWN -> {
                    if (--ticksLeft > 0) {
                        return false;
                    }
                    letGo();
                    if (presses == repeat) {
                        return answer(call);
                    }
                    phase = Phase.UP;
                    ticksLeft = intervalTicks;
                }
                case UP -> {
                    if (--ticksLeft <= 0) {
                        press();
                    }
                }
                default -> {
                }
            }
            return false;
        }

        @Override
        public void cleanup(CallContext call) {
            FeedWatch.forget(listener);
            letGo();
        }

        private void press() {
            Options options = Mc.client().options;
            switch (key) {
                case JUMP -> Steering.tap(Steering.Tap.JUMP, true);
                case SNEAK -> Steering.tap(Steering.Tap.SNEAK, true);
                case SPRINT -> Steering.tap(Steering.Tap.SPRINT, true);
                case USE -> {
                    UseKey.hold();
                    click(options.keyUse);
                }
                case ATTACK -> hold(options.keyAttack);
                case HOTBAR -> hold(options.keyHotbarSlots[slot]);
                case SWAP_OFFHAND -> hold(options.keySwapOffhand);
                case DROP -> hold(options.keyDrop);
                /* The wheel is not a key mapping: the mouse handler moves the selection itself, the same way. */
                case SCROLL_UP, SCROLL_DOWN -> {
                    Inventory inventory = Mc.requirePlayer().getInventory();
                    inventory.setSelectedSlot(ScrollWheelHandler.getNextScrollWheelSelection(
                            key == Key.SCROLL_UP ? 1 : -1, inventory.getSelectedSlot(), Inventory.getSelectionSize()));
                }
            }
            down = true;
            presses++;
            phase = Phase.DOWN;
            ticksLeft = holdTicks;
        }

        private void letGo() {
            if (!down) {
                return;
            }
            down = false;
            Options options = Mc.client().options;
            switch (key) {
                case JUMP -> Steering.tap(Steering.Tap.JUMP, false);
                case SNEAK -> Steering.tap(Steering.Tap.SNEAK, false);
                case SPRINT -> Steering.tap(Steering.Tap.SPRINT, false);
                /* Up through the key, so the keybinding handler lets go of an item in use and tells the server. */
                case USE -> UseKey.release();
                case ATTACK -> options.keyAttack.setDown(false);
                case HOTBAR -> options.keyHotbarSlots[slot].setDown(false);
                case SWAP_OFFHAND -> options.keySwapOffhand.setDown(false);
                case DROP -> options.keyDrop.setDown(false);
                case SCROLL_UP, SCROLL_DOWN -> {
                }
            }
        }

        private static void hold(KeyMapping mapping) {
            mapping.setDown(true);
            click(mapping);
        }

        /**
         * A click is what the handler acts on once, where being down is what it acts on while held. The
         * game counts one on every mapping bound to the physical key, which is how a person's press lands.
         */
        private static void click(KeyMapping mapping) {
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
        }

        private boolean answer(CallContext call) {
            JsonObject data = new JsonObject();
            data.addProperty("key", key.wire());
            data.addProperty("slot", slot);
            data.addProperty("presses", presses);
            data.addProperty("repeat", repeat);
            data.addProperty("holdTicks", holdTicks);
            data.addProperty("intervalTicks", intervalTicks);
            if (after == null) {
                data.add("after", null);
            } else {
                JsonObject waited = after.describe(afterMatched);
                waited.addProperty("waitedMs", waitedMs);
                data.add("after", waited);
            }
            data.add("until", until == null ? null : until.describe(untilMatched));
            data.addProperty("stopped", stopped);
            data.addProperty("selectedSlot", Mc.requirePlayer().getInventory().getSelectedSlot());

            call.ok("pressed " + key.wire() + " " + presses + " of " + repeat + " time(s), " + stopped, data);
            return true;
        }

        private long elapsedMs() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }

        private static String ticks(int count) {
            return count + (count == 1 ? " tick" : " ticks");
        }
    }
}
