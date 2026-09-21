package kr.junhyung.mcagents.botfabric.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.junhyung.mcagents.botfabric.BotConfig;
import kr.junhyung.mcagents.botfabric.event.EventPump;
import kr.junhyung.mcagents.botfabric.session.Session;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

/**
 * The capability list as the handshake carries it. It was once built from every tool the registry
 * held, so one tool written before the catalogue that names it was synced had no hash to report
 * and took the hello down with it -- and with it every other tool on that link.
 *
 * <p>The registry knows which tools it would offer, and is tested on that. This is the assertion
 * that the hello is built from what it offers rather than from everything it holds: without one, a
 * hello rebuilt from {@code tools.all()} puts the whole failure back with every test still green.
 */
class DispatcherTest {

    private static Tool tool(String name) {
        return new ReadTool(name) {
            @Override
            protected JsonObject read(JsonObject args) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static Dispatcher dispatching(ToolRegistry tools) {
        RpcClient client = new RpcClient(new BotConfig("127.0.0.1", 8765, "bot", "", 2000, true, 8080, 8, 1));
        return new Dispatcher(tools, new TaskScheduler(), new Session(client), "bot", "",
                new EventPump(client));
    }

    /** Name to hash, as the far side reads them back out of the hello. */
    private static Map<String, String> offered(JsonObject hello) {
        Map<String, String> capabilities = new LinkedHashMap<>();
        for (JsonElement element : hello.getAsJsonArray("capabilities")) {
            JsonObject capability = element.getAsJsonObject();
            capabilities.put(Json.string(capability, "tool", null), Json.string(capability, "argsHash", null));
        }
        return capabilities;
    }

    @Test
    void aToolTheCatalogueHasNoHashForLeavesTheRestOfTheHandshakeStanding() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool("get-position"));
        tools.register(tool("not-in-the-catalogue"));
        tools.register(tool("jump"));

        /* The fixture is only worth anything while the catalogue really has nothing for that name. */
        assertNull(CatalogHashes.of("not-in-the-catalogue"));
        assertEquals(Map.of("get-position", CatalogHashes.of("get-position"),
                "jump", CatalogHashes.of("jump")), offered(dispatching(tools).hello()));
    }
}
