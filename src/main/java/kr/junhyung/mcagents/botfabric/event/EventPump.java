package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.network.chat.Component;

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
        long id = seq.incrementAndGet();
        long now = System.currentTimeMillis();

        /* A proxy answers a command in chat and nowhere else, so a task can ask to hear it. */
        if (kind.equals("chat")) {
            ChatWatch.heard(message.getString());
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
        event.addProperty("ts", now);
        event.addProperty("firstTs", now);
        event.addProperty("repeats", 1);
        event.addProperty("closed", true);
        client.send(event);
    }
}
