package kr.junhyung.mcagents.botfabric.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The fold and the valves are the server's to set, and a bot that kept its own numbers disagreed
 * with the other kind of bot the moment a server changed them.
 */
class FeedFolderTest {

    private final List<JsonObject> sent = new ArrayList<>();
    private long now;
    private final FeedFolder folder = new FeedFolder(sent::add, () -> now);

    private static JsonObject hello(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject line(String text) {
        JsonObject line = new JsonObject();
        line.addProperty("t", "event");
        line.addProperty("kind", "actionBar");
        line.addProperty("text", text);
        return line;
    }

    @Test
    void aRunIsResentAndClosedOnTheCadenceTheServerGave() {
        folder.configure(hello("{\"repeatFlushMs\": 250, \"events\": {\"actionBar\": true}}"));

        folder.push("actionBar", line("Mana 40"));
        now = 100;
        folder.push("actionBar", line("Mana 40"));
        now = 260;
        folder.push("actionBar", line("Mana 40"));

        assertEquals(2, sent.size(), "a repeat inside one flush went on the wire: " + sent);
        assertEquals(sent.get(0).get("seq"), sent.get(1).get("seq"));
        assertEquals(3, sent.get(1).get("repeats").getAsInt());

        /* Three flushes after the last repeat, not three seconds. */
        now = 260 + 749;
        folder.flush();
        assertTrue(sent.stream().noneMatch(event -> event.get("closed").getAsBoolean()), sent::toString);

        now = 260 + 750;
        folder.flush();
        JsonObject closed = sent.getLast();
        assertTrue(closed.get("closed").getAsBoolean(), closed::toString);
        assertEquals(sent.get(0).get("seq"), closed.get("seq"));
    }

    @Test
    void aShutFeedSendsNothingAndAFeedTheServerDidNotNameIsShut() {
        folder.configure(hello("{\"repeatFlushMs\": 1000, \"events\": {\"chat\": true, \"effect\": false}}"));

        folder.push("effect", line("minecraft:entity.experience_orb.pickup"));
        folder.push("toast", line("First Steps"));
        folder.push("chat", line("hello"));

        assertEquals(1, sent.size(), sent::toString);
        assertEquals("hello", sent.getFirst().get("text").getAsString());
    }
}
