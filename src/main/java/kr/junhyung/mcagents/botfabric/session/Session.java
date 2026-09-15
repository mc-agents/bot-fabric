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

    /** How often a bot in a world sends its whole status even when nothing compared has changed. */
    private static final long REFRESH_MS = 5_000;

    /** What was last reported, so that a change is sent at once. Client thread only. */
    private String reported;
    private long reportedAt;

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
     * Report what a caller would see differently, as it changes, and refresh the rest now and then.
     *
     * <p>A status only went out when the connection changed. Dying does not change it: a bot on the
     * death screen stayed "ready" with the health it joined with. Nor does a move between servers or
     * dimensions behind a proxy: a bot moved from the town to an island still said it was in the
     * town, and one moved after its backend was replaced said (0, 0, 0), the position it had when
     * the join event fired and before the server placed it. So the dimension, brand, game mode and
     * death are compared every tick, and while in a world the whole status goes out every few
     * seconds, which keeps the position and health a caller reads from being old.
     *
     * <p>Nothing is sent without a player. A bot that has just left the world is not a bot that
     * came back to life, and reporting "ready" on the way out would say it was.
     */
    public void noticeWorld() {
        Minecraft minecraft = Mc.client();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null) {
            reported = null;
            return;
        }

        boolean dead = Mc.dead(player);
        Component cause = dead ? Mc.causeOfDeath() : null;
        String seen = String.join("\u0000",
                player.level().dimension().identifier().toString(),
                String.valueOf(connection.serverBrand()),
                minecraft.gameMode == null ? "" : minecraft.gameMode.getPlayerMode().getName(),
                String.valueOf(dead),
                cause == null ? "" : cause.getString());
        long now = System.currentTimeMillis();

        if (!seen.equals(reported) || now - reportedAt >= REFRESH_MS) {
            reported = seen;
            reportedAt = now;
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
