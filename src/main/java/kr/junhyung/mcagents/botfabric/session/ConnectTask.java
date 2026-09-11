package kr.junhyung.mcagents.botfabric.session;

import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.task.Task;
import kr.junhyung.mcagents.botfabric.tool.ToolError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ConnectTask implements Task {
    private final Session session;
    private final String host;
    private final int port;
    private final String username;
    private final long spawnTimeoutMs;

    private long startedAt;

    public ConnectTask(Session session, String host, int port, String username, long spawnTimeoutMs) {
        this.session = session;
        this.host = host;
        this.port = port;
        this.username = username;
        this.spawnTimeoutMs = spawnTimeoutMs;
    }

    @Override
    public String name() {
        return "connect";
    }

    @Override
    public void start(CallContext call) {
        startedAt = System.currentTimeMillis();
        session.rememberTarget(host, port, username);
        session.applyUsername(username);
        session.report("connecting");

        Minecraft minecraft = Mc.client();
        ServerAddress target = new ServerAddress(host, port);
        ServerData data = new ServerData(username + "@" + host, host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, target, data, false, null);
    }

    @Override
    public boolean tick(CallContext call) {
        Minecraft minecraft = Mc.client();
        if (minecraft.screen instanceof DisconnectedScreen) {
            String reason = minecraft.screen.getTitle().getString();
            session.report("disconnected", reason, reason);
            call.fail(ToolError.TOOL, "REFUSED", "the server refused the connection: " + reason, true);
            return true;
        }
        if (minecraft.player != null && minecraft.level != null && minecraft.getConnection() != null) {
            session.report("ready");
            call.ok("joined " + host + ":" + port + " as " + username);
            return true;
        }
        if (System.currentTimeMillis() - startedAt > spawnTimeoutMs) {
            session.report("disconnected", "spawn timeout", null);
            call.fail(ToolError.TIMEOUT, "SPAWN_TIMEOUT",
                    "no spawn within " + spawnTimeoutMs + "ms of connecting to " + host + ":" + port, true);
            return true;
        }
        return false;
    }
}
