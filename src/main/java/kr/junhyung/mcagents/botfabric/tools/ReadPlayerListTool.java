package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Who else is online, from the tab list the server keeps up to date. */
public final class ReadPlayerListTool extends ReadTool {

    public ReadPlayerListTool() {
        super("read-player-list");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String self = Mc.requirePlayerEvenIfDead().getGameProfile().name();

        JsonArray players = new JsonArray();
        for (PlayerInfo info : Mc.requireConnection().getOnlinePlayers()) {
            JsonObject player = new JsonObject();
            String name = info.getProfile().name();
            player.addProperty("name", name);
            player.addProperty("gameMode", info.getGameMode().getName());
            player.addProperty("ping", info.getLatency());
            player.addProperty("self", name.equals(self));

            /*
            The username is the identity every other tool takes; what the tab list draws is where a
            server puts a rank, and it is written in the pack's own font as often as not.
            */
            Component drawn = info.getTabListDisplayName();
            player.addProperty("displayName", drawn == null ? null : drawn.getString());
            player.add("displayNameComponent", drawn == null ? JsonNull.INSTANCE : Segments.raw(drawn));
            players.add(player);
        }

        JsonObject data = new JsonObject();
        data.add("players", players);
        return data;
    }
}
