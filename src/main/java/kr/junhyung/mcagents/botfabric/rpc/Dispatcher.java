package kr.junhyung.mcagents.botfabric.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.BotFabricClient;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.event.EventPump;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.session.ConnectTask;
import kr.junhyung.mcagents.botfabric.session.Session;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import kr.junhyung.mcagents.botfabric.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Dispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric/dispatch");

    private final ToolRegistry tools;
    private final TaskScheduler scheduler;
    private final Session session;
    private final String botName;
    private final EventPump events;

    private final Map<String, CallContext> inFlight = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> deadlines = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timers =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "botfabric-deadlines");
                thread.setDaemon(true);
                return thread;
            });

    private volatile RpcClient client;

    public Dispatcher(ToolRegistry tools, TaskScheduler scheduler, Session session, String botName,
            EventPump events) {
        this.tools = tools;
        this.scheduler = scheduler;
        this.session = session;
        this.botName = botName;
        this.events = events;
    }

    public JsonObject hello() {
        JsonArray protocols = new JsonArray();
        protocols.add(1);

        JsonArray capabilities = new JsonArray();
        for (Tool tool : tools.all().values()) {
            JsonObject capability = new JsonObject();
            capability.addProperty("tool", tool.name());
            capability.addProperty("argsHash", tool.argsHash());
            capabilities.add(capability);
        }

        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        hello.add("protocols", protocols);
        hello.addProperty("botName", botName);
        hello.addProperty("kind", "fabric");
        hello.addProperty("agentVersion", BotFabricClient.AGENT_VERSION);
        hello.addProperty("mcVersion", BotFabricClient.MINECRAFT_VERSION);
        hello.addProperty("catalogVersion", CatalogHashes.CATALOG_VERSION);
        hello.add("capabilities", capabilities);
        hello.add("features", Json.array("blob"));
        return hello;
    }

    public void handle(RpcClient client, JsonObject message) {
        this.client = client;
        String type = Json.string(message, "t", "");
        switch (type) {
            case "helloOk" -> onHelloOk(message);
            case "helloErr" -> LOGGER.error("server refused the link: {} {}",
                    Json.string(message, "code", "?"), Json.string(message, "message", ""));
            case "connect" -> onConnect(client, message);
            case "call" -> onCall(client, message);
            case "cancel" -> onCancel(message);
            case "disconnect" -> onDisconnect(client, message);
            case "shutdown" -> onShutdown(message);
            case "ping" -> onPing(client, message);
            /*
            A breach of the wire contract, not a failed call. The link closes behind it, so there
            is nothing to answer -- but reconnecting and sending the same thing again is what a
            bot does when it does not read this, and the code is the only clue anybody gets.
            */
            case "fault" -> LOGGER.error("mcp-server closed the link on a protocol fault: {} {}",
                    Json.string(message, "code", "?"), Json.string(message, "message", ""));
            default -> LOGGER.warn("unknown message type: {}", type);
        }
    }

    public void onUnlinked() {
        scheduler.abortAll("the link to the MCP server dropped");
        inFlight.values().forEach(call -> call.fail(ToolError.BOT, "LINK_LOST", "the link dropped", true));
        inFlight.clear();
        deadlines.values().forEach(future -> future.cancel(false));
        deadlines.clear();
    }

    private void onHelloOk(JsonObject message) {
        LOGGER.info("linked as session {}", Json.string(message, "sessionId", "?"));
        events.configure(message);
        /* "idle" is the protocol's word for linked and in no world. */
        session.report("idle");
    }

    private void onConnect(RpcClient client, JsonObject message) {
        String id = Json.requireString(message, "id");
        String host = Json.requireString(message, "host");
        int port = Json.integer(message, "port", 25565);
        String username = Json.string(message, "username", botName);
        long spawnTimeout = Json.number(message, "spawnTimeoutMs", 60000);

        CallContext call = track(client, id, "connect", spawnTimeout + 2000);
        if (call == null) {
            return;
        }
        scheduler.submit(new ConnectTask(session, host, port, username, spawnTimeout), call);
    }

    private void onCall(RpcClient client, JsonObject message) {
        String id = Json.requireString(message, "id");
        String toolName = Json.requireString(message, "tool");
        long deadline = Json.number(message, "deadlineMs", 30000);

        CallContext call = track(client, id, toolName, deadline);
        if (call == null) {
            return;
        }

        Tool tool = tools.find(toolName);
        if (tool == null) {
            call.fail(ToolError.UNSUPPORTED, "NOT_IMPLEMENTED",
                    toolName + " is not implemented by the fabric bot", false);
            return;
        }
        try {
            tool.invoke(call, Json.object(message, "args"));
        } catch (Throwable thrown) {
            call.fail(thrown);
        }
    }

    private void onCancel(JsonObject message) {
        CallContext call = inFlight.get(Json.string(message, "id", ""));
        if (call != null) {
            call.cancelled(Json.string(message, "reason", "cancelled"));
        }
    }

    private void onDisconnect(RpcClient client, JsonObject message) {
        String id = Json.requireString(message, "id");
        String reason = Json.string(message, "reason", "asked to leave");

        CallContext call = track(client, id, "disconnect", 10000);
        if (call == null) {
            return;
        }
        Mc.immediate(call, () -> {
            if (Mc.client().level != null) {
                Mc.client().disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
            }
            /* Told to leave, so nothing went wrong: linked and in no world is idle. */
            session.report("idle", reason, null);
            call.ok("left the game: " + reason);
        });
    }

    private void onShutdown(JsonObject message) {
        LOGGER.info("shutdown requested: {}", Json.string(message, "reason", ""));
        scheduler.abortAll("the bot is shutting down");
        Mc.client().execute(() -> Mc.client().stop());
    }

    private void onPing(RpcClient client, JsonObject message) {
        JsonObject pong = new JsonObject();
        pong.addProperty("t", "pong");
        /* The nonce is a number the server allocated, and busy is how many calls are in flight. */
        pong.addProperty("nonce", Json.number(message, "nonce", 0L));
        pong.addProperty("ts", System.currentTimeMillis());
        pong.addProperty("busy", inFlight.size());
        client.send(pong);
    }

    private CallContext track(RpcClient client, String id, String tool, long deadlineMs) {
        CallContext call = new CallContext(client, id, tool, deadlineMs);
        if (inFlight.putIfAbsent(id, call) != null) {
            LOGGER.error("duplicate call id: {}", id);
            return null;
        }
        ScheduledFuture<?> timer = timers.schedule(call::timedOut, deadlineMs, TimeUnit.MILLISECONDS);
        deadlines.put(id, timer);
        FrameBudget.boost();
        call.onSettled(() -> {
            FrameBudget.release();
            inFlight.remove(id);
            ScheduledFuture<?> armed = deadlines.remove(id);
            if (armed != null) {
                armed.cancel(false);
            }
        });
        return call;
    }
}
