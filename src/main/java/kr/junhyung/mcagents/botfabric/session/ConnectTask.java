package kr.junhyung.mcagents.botfabric.session;

import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.DisconnectedScreenAccessor;
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
    private boolean dialled;

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
    }

    /*
    Not before the client has finished loading. When the first resource load ends the client puts up
    its initial screen, the title screen, whatever is on screen by then; a join that raced it was left
    in the world under a title screen that Escape does not close, and every tool that refuses to act
    behind a screen refused from then on. That only happened where loading is slow, on a busy runner.
    */
    private void dial() {
        Minecraft minecraft = Mc.client();
        ServerAddress target = new ServerAddress(host, port);
        ServerData data = new ServerData(username + "@" + host, host + ":" + port, ServerData.Type.OTHER);
        /*
        Without this the client asks. A server that requires its pack puts up "Proceed / Disconnect"
        during the join, nobody clicks it, and join-server gives up with a spawn timeout while the
        client sits on a screen -- which is what a real server did, and what a screenshot of the
        stuck bot showed in one call. A bot has no one to ask, and the pack is the whole interface
        on a server that draws with custom glyphs.
        */
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, target, data, false, null);
    }

    @Override
    public boolean tick(CallContext call) {
        Minecraft minecraft = Mc.client();
        if (!dialled && minecraft.isGameLoadFinished()) {
            dialled = true;
            dial();
        }
        if (Mc.screen() instanceof DisconnectedScreen disconnected) {
            /*
            The title alone is "Connection Lost". What the server or the proxy said -- "There are no
            available servers", a kick message, a whitelist -- is the reason under it, and it is the
            part that says where to look.
            */
            String title = disconnected.getTitle().getString();
            String said = ((DisconnectedScreenAccessor) disconnected).mcagents$details().reason().getString();
            String reason = said.isBlank() || said.equals(title) ? title : title + ": " + said;
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
