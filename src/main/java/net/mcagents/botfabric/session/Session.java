package net.mcagents.botfabric.session;

import com.google.gson.JsonObject;
import net.mcagents.botfabric.Mc;
import net.mcagents.botfabric.mixin.MinecraftAccessor;
import net.mcagents.botfabric.rpc.RpcClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Session {
    private final RpcClient client;
    private volatile String address = "";
    private volatile String username = "";

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
        status.addProperty("mcVersion", net.mcagents.botfabric.BotFabricClient.MINECRAFT_VERSION);
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
        if (minecraft.gameMode != null) {
            status.addProperty("gameMode", minecraft.gameMode.getPlayerMode().getName());
        }
        status.addProperty("dimension", player.level().dimension().identifier().toString());
        status.addProperty("health", player.getHealth());
        status.addProperty("food", player.getFoodData().getFoodLevel());

        JsonObject position = new JsonObject();
        position.addProperty("x", player.getX());
        position.addProperty("y", player.getY());
        position.addProperty("z", player.getZ());
        status.add("position", position);
    }
}
