package kr.junhyung.mcagents.botfabric;

/**
 * Where to dial and what to call itself, from the environment.
 *
 * <p>The names are the contract in mcp-server's {@code docs/bot-protocol.md}, under "How a bot is
 * told where to dial". They were this mod's own until the operator, this bot and the mineflayer
 * bot turned out to have three different sets, which meant a pod the operator built dialled its
 * own loopback and waited there. {@code BOT_RPC_ENABLED} is not in the contract: it is this mod's,
 * for running the client by hand with nothing driving it.
 */
public record BotConfig(String host, int port, String botName, long reconnectDelayMs, boolean enabled,
                        int healthPort) {
    public static BotConfig fromEnvironment() {
        String host = env("MCP_SERVER_HOST", "127.0.0.1");
        int port = Integer.parseInt(env("MCP_SERVER_PORT", "8765"));
        String name = env("BOT_NAME", "fabric_bot");
        long delay = Long.parseLong(env("RECONNECT_MIN_MS", "2000"));
        boolean enabled = !"false".equalsIgnoreCase(env("BOT_RPC_ENABLED", "true"));
        int healthPort = Integer.parseInt(env("HEALTH_PORT", "8080"));
        return new BotConfig(host, port, name, delay, enabled, healthPort);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key.toLowerCase().replace('_', '.'));
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
