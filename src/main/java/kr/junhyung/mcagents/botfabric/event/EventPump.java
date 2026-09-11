package kr.junhyung.mcagents.botfabric.event;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class EventPump {
    private static final int RING = 200;

    private final RpcClient client;
    private final AtomicLong seq = new AtomicLong();
    private final Deque<Line> recent = new ArrayDeque<>();

    public EventPump(RpcClient client) {
        this.client = client;
    }

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) ->
                emit("chat", sender == null ? "system" : sender.name(), message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                emit(overlay ? "actionBar" : "chat", "system", message));
    }

    public long mark() {
        synchronized (recent) {
            return recent.isEmpty() ? 0 : recent.peekLast().seq();
        }
    }

    public List<String> since(long mark) {
        List<String> lines = new ArrayList<>();
        synchronized (recent) {
            for (Line line : recent) {
                if (line.seq() > mark) {
                    lines.add(line.text());
                }
            }
        }
        return lines;
    }

    private void emit(String kind, String source, Component message) {
        long id = seq.incrementAndGet();
        long now = System.currentTimeMillis();
        String text = message.getString();

        synchronized (recent) {
            recent.addLast(new Line(id, text));
            while (recent.size() > RING) {
                recent.removeFirst();
            }
        }

        JsonObject event = new JsonObject();
        event.addProperty("t", "event");
        event.addProperty("seq", id);
        event.addProperty("kind", kind);
        event.addProperty("source", source);
        event.addProperty("text", text);
        event.add("segments", Segments.of(message));
        event.addProperty("ts", now);
        event.addProperty("firstTs", now);
        event.addProperty("repeats", 1);
        event.addProperty("closed", true);
        client.send(event);
    }

    private record Line(long seq, String text) {
    }
}
