package kr.junhyung.mcagents.botfabric.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalStateException("tool registered twice: " + tool.name());
        }
    }

    public Tool find(String name) {
        return tools.get(name);
    }

    public Map<String, Tool> all() {
        return Map.copyOf(tools);
    }

    /**
     * What this bot offers at the handshake: the tools it implements that the catalogue has a hash
     * for, by name, in the order they were registered.
     *
     * <p>A tool with no hash is left out rather than reported. There is nothing to report for it --
     * a hash of its own opinion of the schema agrees with nothing -- and the alternative of
     * refusing to build the list at all takes the link down, which costs every other tool on it.
     */
    public Map<String, String> capabilities() {
        Map<String, String> offered = new LinkedHashMap<>();
        tools.forEach((name, tool) -> {
            String hash = tool.argsHash();
            if (hash != null) {
                offered.put(name, hash);
            }
        });
        return offered;
    }

    /** The other half: what was registered and is not offered, for whoever has to be told why. */
    public List<String> unhashed() {
        List<String> missing = new ArrayList<>();
        tools.forEach((name, tool) -> {
            if (tool.argsHash() == null) {
                missing.add(name);
            }
        });
        return missing;
    }
}
