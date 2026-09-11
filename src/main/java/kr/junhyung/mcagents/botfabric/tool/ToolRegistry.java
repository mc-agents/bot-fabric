package kr.junhyung.mcagents.botfabric.tool;

import java.util.LinkedHashMap;
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
}
