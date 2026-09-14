package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.text.Dialogs;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every feed the bot sees, pushed as it arrives.
 *
 * <p>Nothing is kept here. The ring buffer a caller reads is mcp-server's, and it is the server that
 * decides what counts as arriving after a command was sent. A second buffer on this side used to
 * exist for run-command, which made the caller read the server's reply twice.
 */
public final class EventPump {

    /** The feeds a run is folded on. Chat is never folded: the same line twice is information. */
    private static final Set<String> FOLDED = Set.of("actionBar", "title", "dialog");

    /** How often a run that stays open goes on the wire again. */
    private static final long FLUSH_MS = 1_000;

    /** A run nothing has repeated for this many flushes has stopped showing, whatever the server meant. */
    private static final long STALE_FLUSHES = 3;

    private final RpcClient client;
    private final AtomicLong seq = new AtomicLong();
    /** The run each folded feed has open. A title and a subtitle share the feed, so one replaces the other. */
    private final Map<String, Run> runs = new HashMap<>();

    public EventPump(RpcClient client) {
        this.client = client;
    }

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                emit("chat", sender == null ? "system" : sender.name(), message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                emit(overlay ? "actionBar" : "chat", "system", message));

        /* Titles, sounds and particles have no Fabric API event; a mixin hands them over here. */
        Feeds.listen(this);
    }

    public void emit(String kind, String source, Component message) {
        emit(kind, source, message, null);
    }

    /**
     * A dialog, as structure rather than as a sentence.
     *
     * <p>mcp-server reads the title, the body and the buttons out of it and writes the line: a
     * dialog is not one piece of text, and the order its parts are read in is presentation. The
     * title rides along as the text field so a server that changed nothing else still has a
     * fallback to show.
     */
    public void dialog(Dialog shown) {
        emit("dialog", "dialog", shown.common().title(), Dialogs.raw(shown));
    }

    /**
     * An advancement's toast, with the id beside the title.
     *
     * <p>The title is what the toast draws and the id is what a caller can name: a server that
     * grants one when a quest is done draws its title in the pack's own font.
     *
     * <p>The client only puts one up for an advancement with a display, but the toast itself does
     * not insist, and this runs inside the toast manager: a missing display is skipped rather than
     * thrown, because a feed that took the client down with it would cost more than the line.
     */
    public void advancement(AdvancementHolder advancement) {
        DisplayInfo display = advancement.value().display().orElse(null);
        if (display == null) {
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("id", advancement.id().toString());
        data.addProperty("frame", display.getType().getSerializedName());
        data.addProperty("description", display.getDescription().getString());
        data.add("descriptionComponent", Segments.raw(display.getDescription()));

        emit("toast", "advancement", display.getTitle(), data);
    }

    public void recipe(ItemStack result) {
        JsonObject data = new JsonObject();
        data.addProperty("item", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());

        emit("toast", "recipe", result.getHoverName(), data);
    }

    /**
     * Re-send the runs that are due, and close the ones nothing has repeated in a while. Called
     * every client tick, which is far more often than anything here comes due.
     */
    public synchronized void flush() {
        long now = System.currentTimeMillis();

        for (Iterator<Run> open = runs.values().iterator(); open.hasNext(); ) {
            Run run = open.next();

            if (now - run.seen >= FLUSH_MS * STALE_FLUSHES) {
                client.send(run.frame(true));
                open.remove();
            } else if (now - run.sent >= FLUSH_MS) {
                run.sent = now;
                client.send(run.frame(false));
            }
        }
    }

    /** The bot left the world, so nothing it was showing is showing any more. */
    public synchronized void closeAll() {
        long now = System.currentTimeMillis();

        for (Run run : runs.values()) {
            run.seen = now;
            client.send(run.frame(true));
        }
        runs.clear();
    }

    private synchronized void emit(String kind, String source, Component message, JsonElement data) {
        /* A proxy answers a command in chat and nowhere else, so a task can ask to hear it. */
        if (kind.equals("chat")) {
            ChatWatch.heard(message.getString());
            /* And the component itself, because a line with a click event on it can be pressed. */
            ChatLines.heard(message);
        }

        /* A press waiting on a line reacts here, before the tick that reads the keys, not a round trip later. */
        if (kind.equals("actionBar") || kind.equals("title") || kind.equals("effect")) {
            FeedWatch.saw(kind, kind.equals("effect") ? message.getString() : Segments.readable(message));
        }

        JsonObject event = new JsonObject();
        event.addProperty("t", "event");
        event.addProperty("kind", kind);
        event.addProperty("source", source);
        event.addProperty("text", message.getString());
        /*
        Only the feeds a server draws with stacked glyphs. Chat is prose, and splitting it at every
        style change turns one sentence into a dozen fragments joined by separators.
        */
        if (kind.equals("actionBar") || kind.equals("title")) {
            event.add("segments", Segments.of(message));
        }
        /*
        And the component itself, for every feed. mcp-server flattens it, so the rule lives in one
        place; the segments above stay for the one thing it cannot do, which is resolve a translate
        key without the game's language table.
        */
        event.add("component", Segments.raw(message));
        if (data != null) {
            event.add("data", data);
        }

        if (FOLDED.contains(kind)) {
            fold(kind, event);
            return;
        }

        long now = System.currentTimeMillis();
        event.addProperty("seq", seq.incrementAndGet());
        event.addProperty("ts", now);
        event.addProperty("firstTs", now);
        event.addProperty("repeats", 1);
        event.addProperty("closed", true);
        client.send(event);
    }

    /**
     * An action bar redrawn twenty times a second is one thing showing, not twenty things happening.
     *
     * <p>The run keeps its sequence number and every re-send reuses it, so mcp-server updates that
     * line in place rather than waking a waiter on each frame -- a wait would otherwise match a line
     * that was already up before it was called. Sending every packet as a run of its own closed on
     * arrival did exactly that, and put twenty lines a second into a buffer of two hundred.
     */
    private void fold(String kind, JsonObject line) {
        long now = System.currentTimeMillis();
        Run run = runs.get(kind);

        if (run != null && run.line.equals(line)) {
            run.repeats++;
            run.seen = now;
            if (now - run.sent >= FLUSH_MS) {
                run.sent = now;
                client.send(run.frame(false));
            }
            return;
        }

        if (run != null) {
            client.send(run.frame(true));
        }
        Run opened = new Run(seq.incrementAndGet(), line, now);
        runs.put(kind, opened);
        client.send(opened.frame(false));
    }

    /** One folded run: the line as it was first seen, and how often and how lately it was seen again. */
    private static final class Run {
        private final long seq;
        private final JsonObject line;
        private final long first;
        private long seen;
        private long sent;
        private int repeats = 1;

        private Run(long seq, JsonObject line, long now) {
            this.seq = seq;
            this.line = line;
            this.first = now;
            this.seen = now;
            this.sent = now;
        }

        private JsonObject frame(boolean closed) {
            JsonObject event = line.deepCopy();
            event.addProperty("seq", seq);
            event.addProperty("ts", seen);
            event.addProperty("firstTs", first);
            event.addProperty("repeats", repeats);
            event.addProperty("closed", closed);
            return event;
        }
    }
}
