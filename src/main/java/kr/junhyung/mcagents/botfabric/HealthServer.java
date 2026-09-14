package kr.junhyung.mcagents.botfabric;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /healthz} and {@code /readyz}, on the port the pod spec names.
 *
 * <p>Readiness is the link, not the process. It is the only thing outside the wire protocol that
 * knows whether a bot has been accepted, and the operator's {@code LINK} column is exactly this
 * probe: with nothing serving it, a bot that had linked perfectly well showed as Lost.
 *
 * <p>Liveness is the client thread. A bot waiting for an MCP server to come back is healthy and not
 * ready, and restarting it would throw away a Minecraft client that takes a minute to boot. A client
 * thread that stopped ticking is another matter: every call is answered there, so the pod stayed up,
 * linked and ready while every tool and restart-bot timed out, and only deleting the pod got it back.
 * Before the first tick the client is still loading, which is slow, not stuck.
 */
public final class HealthServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric/health");

    /** Far longer than any tick a busy client takes, and short of the probe giving up for a minute. */
    private static final long STALLED_MS = 60_000;

    private static volatile long lastTick;

    private HealthServer() {
    }

    public static void start(int port, BooleanSupplier linked) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/healthz", exchange -> {
                long since = lastTick == 0 ? 0 : System.currentTimeMillis() - lastTick;
                if (since > STALLED_MS) {
                    respond(exchange, 503, "the client thread has not ticked for " + since + "ms");
                } else {
                    respond(exchange, 200, "ok");
                }
            });
            server.createContext("/readyz", exchange -> {
                boolean ready = linked.getAsBoolean();
                respond(exchange, ready ? 200 : 503, ready ? "ready" : "not linked to mcp-server");
            });

            server.setExecutor(runnable -> Thread.ofVirtual().start(runnable));
            server.start();

            LOGGER.info("health server listening on {}", port);
        } catch (IOException e) {
            /*
            Not fatal. A bot that cannot serve a probe is still a bot that can be driven, and in a
            pod the probe failing is how that gets noticed anyway.
            */
            LOGGER.error("could not start the health server on {}: {}", port, e.toString());
        }
    }

    /** Called from the client thread at the end of every tick. */
    public static void ticked() {
        lastTick = System.currentTimeMillis();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = (body + "\n").getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
