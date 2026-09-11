package net.mcagents.botfabric;

import net.mcagents.botfabric.rpc.CallContext;
import net.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

public final class Mc {
    private Mc() {
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    public static LocalPlayer requirePlayer() {
        LocalPlayer player = client().player;
        if (player == null) {
            throw ToolException.notInGame();
        }
        return player;
    }

    public static ClientPacketListener requireConnection() {
        ClientPacketListener connection = client().getConnection();
        if (connection == null) {
            throw ToolException.notInGame();
        }
        return connection;
    }

    public static void immediate(CallContext call, ClientBody body) {
        client().submit(() -> {
            if (call.settled()) {
                return;
            }
            try {
                body.run();
            } catch (Throwable thrown) {
                call.fail(thrown);
            }
        }).exceptionally(thrown -> {
            call.fail(thrown);
            return null;
        });
    }

    @FunctionalInterface
    public interface ClientBody {
        void run() throws Exception;
    }
}
