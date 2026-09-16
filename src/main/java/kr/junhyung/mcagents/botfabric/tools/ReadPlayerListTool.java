package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Who else is online, from the tab list the server keeps up to date.
 *
 * <p>Only the players the server lists, in name order, which is what the other kind of bot
 * answers. An entry the server sends unlisted is one it keeps for an NPC's skin or for a player
 * on another backend, not somebody in the tab list; and the client holds them in a hash map,
 * so unsorted they came back in a different order every call.
 */
public final class ReadPlayerListTool extends ReadTool {

    public ReadPlayerListTool() {
        super("read-player-list");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String self = Mc.requirePlayerEvenIfDead().getGameProfile().name();

        List<PlayerInfo> listed = new ArrayList<>(Mc.requireConnection().getListedOnlinePlayers());
        listed.sort(Comparator.comparing(info -> info.getProfile().name()));

        JsonArray players = new JsonArray();
        for (PlayerInfo info : listed) {
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
