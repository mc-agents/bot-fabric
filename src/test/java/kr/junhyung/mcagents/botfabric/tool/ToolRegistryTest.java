package kr.junhyung.mcagents.botfabric.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A tool written before the catalogue that names it was synced has no hash here to report, and the
 * hello carries a hash for every tool at once. That one tool used to take the handshake down with
 * it, and the link it was on carried every other tool this bot has, so adding a tool ahead of a
 * sync broke the whole run rather than the one thing that was not ready.
 */
class ToolRegistryTest {

    private static Tool tool(String name) {
        return new ReadTool(name) {
            @Override
            protected JsonObject read(JsonObject args) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void aToolTheCatalogueHasNoHashForIsLeftOutAndTheRestAreStillOffered() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(tool("get-position"));
        tools.register(tool("not-in-the-catalogue"));
        tools.register(tool("jump"));

        Map<String, String> offered = tools.capabilities();

        /* The fixture is only worth anything while the catalogue really has nothing for that name. */
        assertNull(CatalogHashes.of("not-in-the-catalogue"));
        assertEquals(List.of("get-position", "jump"), List.copyOf(offered.keySet()));
        assertEquals(CatalogHashes.of("get-position"), offered.get("get-position"));
    }
}
