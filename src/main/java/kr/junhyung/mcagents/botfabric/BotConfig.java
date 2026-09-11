package kr.junhyung.mcagents.botfabric;

public record BotConfig(String host, int port, String botName, long reconnectDelayMs, boolean enabled) {
    public static BotConfig fromEnvironment() {
        String host = env("MCAGENTS_RPC_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("MCAGENTS_RPC_PORT", "8765"));
        String name = env("MCAGENTS_BOT_NAME", "fabric_bot");
        long delay = Long.parseLong(env("MCAGENTS_RPC_RECONNECT_MS", "2000"));
        boolean enabled = !"false".equalsIgnoreCase(env("MCAGENTS_RPC_ENABLED", "true"));
        return new BotConfig(host, port, name, delay, enabled);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key.toLowerCase().replace('_', '.'));
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
