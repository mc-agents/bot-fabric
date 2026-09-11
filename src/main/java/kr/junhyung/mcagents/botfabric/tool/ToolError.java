package kr.junhyung.mcagents.botfabric.tool;

public enum ToolError {
    TOOL("tool"),
    TIMEOUT("timeout"),
    CANCELLED("cancelled"),
    UNSUPPORTED("unsupported"),
    ARGS("args"),
    BOT("bot"),
    INTERNAL("internal");

    private final String wireName;

    ToolError(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
