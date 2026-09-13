package kr.junhyung.mcagents.botfabric.session;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.MinecraftAccessor;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Session {
    private final RpcClient client;
    private volatile String address = "";
    private volatile String username = "";

    /** What was last reported about death, so that only a change is sent. Client thread only. */
    private boolean reportedDead;
    private String reportedCause;

    public Session(RpcClient client) {
        this.client = client;
    }

    public String address() {
        return address;
    }

    public String username() {
        return username;
    }

    public void rememberTarget(String host, int port, String username) {
        this.address = host + ":" + port;
        this.username = username;
    }

    public void applyUsername(String name) {
        UUID id = UUIDUtil.createOfflinePlayerUUID(name);
        User user = new User(name, id, "", Optional.empty(), Optional.empty());
        MinecraftAccessor accessor = (MinecraftAccessor) Minecraft.getInstance();
        accessor.botfabric$setUser(user);
        accessor.botfabric$setProfileFuture(CompletableFuture.completedFuture(null));
    }

    /**
     * Report a death, and the respawn after it, as they happen.
     *
     * <p>A status only went out when the connection changed, and dying does not change it: a bot on
     * the death screen stayed "ready" in get-bot-status with the health it joined with. The cause is
     * compared too, because the health that says dead arrives a packet before the screen that says
     * of what, and the first report would otherwise be the only one.
     *
     * <p>Nothing is sent without a player. A bot that has just left the world is not a bot that
     * came back to life, and reporting "ready" on the way out would say it was.
     */
    public void noticeDeath() {
        LocalPlayer player = Mc.client().player;
        if (player == null) {
            reportedDead = false;
            reportedCause = null;
            return;
        }

        boolean dead = Mc.dead(player);
        Component cause = dead ? Mc.causeOfDeath() : null;
        String causeText = cause == null ? null : cause.getString();

        if (dead != reportedDead || !Objects.equals(causeText, reportedCause)) {
            reportedDead = dead;
            reportedCause = causeText;
            report("ready");
        }
    }

    public void report(String state) {
        report(state, null, null);
    }

    public void report(String state, String reason, String lastError) {
        JsonObject status = new JsonObject();
        status.addProperty("t", "status");
        status.addProperty("state", state);
        status.addProperty("ts", System.currentTimeMillis());
        if (!address.isEmpty()) {
            status.addProperty("address", address);
        }
        if (!username.isEmpty()) {
            status.addProperty("username", username);
        }
        status.addProperty("mcVersion", kr.junhyung.mcagents.botfabric.BotFabricClient.MINECRAFT_VERSION);
        if (reason != null) {
            status.addProperty("reason", reason);
        }
        if (lastError != null) {
            status.addProperty("lastError", lastError);
        }
        describeWorld(status);
        client.send(status);
    }

    private void describeWorld(JsonObject status) {
        Minecraft minecraft = Mc.client();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null) {
            return;
        }
        /* The brand comes in as a plugin message during login, and it is what tells a caller it is
           looking at Paper rather than vanilla. The other kind of bot has always sent it. */
        if (connection.serverBrand() != null) {
            status.addProperty("serverBrand", connection.serverBrand());
        }
        if (minecraft.gameMode != null) {
            status.addProperty("gameMode", minecraft.gameMode.getPlayerMode().getName());
        }
        status.addProperty("dimension", player.level().dimension().identifier().toString());
        status.addProperty("health", player.getHealth());
        status.addProperty("food", player.getFoodData().getFoodLevel());
        boolean dead = Mc.dead(player);
        status.addProperty("dead", dead);
        Component cause = dead ? Mc.causeOfDeath() : null;
        if (cause != null) {
            status.addProperty("causeOfDeath", cause.getString());
        }

        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        status.add("position", position);
    }
}
