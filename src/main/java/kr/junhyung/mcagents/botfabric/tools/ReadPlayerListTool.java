package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Who else is online, from the tab list the server keeps up to date. */
public final class ReadPlayerListTool extends ReadTool {

    public ReadPlayerListTool() {
        super("read-player-list");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String self = Mc.requirePlayer().getGameProfile().name();

        JsonArray players = new JsonArray();
        for (PlayerInfo info : Mc.requireConnection().getOnlinePlayers()) {
            JsonObject player = new JsonObject();
            String name = info.getProfile().name();
            player.addProperty("name", name);
            player.addProperty("gameMode", info.getGameMode().getName());
            player.addProperty("ping", info.getLatency());
            player.addProperty("self", name.equals(self));
            players.add(player);
        }

        JsonObject data = new JsonObject();
        data.add("players", players);
        return data;
    }
}
