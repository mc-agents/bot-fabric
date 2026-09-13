package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.text.Dialogs;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.Dialog;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Every feed the bot sees, pushed as it arrives.
 *
 * <p>Nothing is kept here. The ring buffer a caller reads is mcp-server's, and it is the server that
 * decides what counts as arriving after a command was sent. A second buffer on this side used to
 * exist for run-command, which made the caller read the server's reply twice.
 */
public final class EventPump {

    private final RpcClient client;
    private final AtomicLong seq = new AtomicLong();

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

    private void emit(String kind, String source, Component message, JsonElement data) {
        long id = seq.incrementAndGet();
        long now = System.currentTimeMillis();

        /* A proxy answers a command in chat and nowhere else, so a task can ask to hear it. */
        if (kind.equals("chat")) {
            ChatWatch.heard(message.getString());
            /* And the component itself, because a line with a click event on it can be pressed. */
            ChatLines.heard(message);
        }

        JsonObject event = new JsonObject();
        event.addProperty("t", "event");
        event.addProperty("seq", id);
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
        event.addProperty("ts", now);
        event.addProperty("firstTs", now);
        event.addProperty("repeats", 1);
        event.addProperty("closed", true);
        client.send(event);
    }
}
