package kr.junhyung.mcagents.botfabric.tool;

public class ToolException extends RuntimeException {
    private final ToolError errorClass;
    private final String code;
    private final boolean retryable;

    public ToolException(ToolError errorClass, String code, String message, boolean retryable) {
        super(message);
        this.errorClass = errorClass;
        this.code = code;
        this.retryable = retryable;
    }

    public static ToolException refused(String code, String message) {
        return new ToolException(ToolError.TOOL, code, message, false);
    }

    public static ToolException badArgs(String message) {
        return new ToolException(ToolError.ARGS, "BAD_ARGS", message, false);
    }

    public static ToolException notInGame() {
        return new ToolException(ToolError.TOOL, "NOT_IN_GAME", "the bot is not in a world", true);
    }

    public ToolError errorClass() {
        return errorClass;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
