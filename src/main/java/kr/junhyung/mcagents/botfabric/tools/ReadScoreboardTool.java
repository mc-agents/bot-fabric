package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/** What a server is drawing in a display slot, usually the sidebar. */
public final class ReadScoreboardTool extends ReadTool {

    public ReadScoreboardTool() {
        super("read-scoreboard");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String wanted = args.has("slot") ? args.get("slot").getAsString() : "sidebar";
        DisplaySlot slot = DisplaySlot.CODEC.byName(wanted);

        JsonObject data = new JsonObject();
        data.addProperty("slot", wanted);

        Scoreboard scoreboard = Mc.requirePlayerEvenIfDead().level().getScoreboard();
        Objective objective = slot == null ? null : scoreboard.getDisplayObjective(slot);

        /* Nothing displayed is a state, not a failure, so the server says the words. */
        if (objective == null) {
            data.add("board", JsonNull.INSTANCE);
            return data;
        }

        JsonArray entries = new JsonArray();
        for (PlayerScoreEntry score : scoreboard.listPlayerScores(objective)) {
            /*
            ownerName() and not owner(): a server may give an entry a name of its own, and that is
            what the sidebar draws. Reading the owner instead reported the key -- a uuid or an
            internal handle on the servers that use one -- where the screen showed a player's name.
            */
            Component name = score.ownerName();

            JsonObject entry = new JsonObject();
            entry.addProperty("name", name.getString());
            entry.add("nameComponent", Segments.raw(name));
            entry.addProperty("score", score.value());
            entries.add(entry);
        }

        Component title = objective.getDisplayName();

        JsonObject board = new JsonObject();
        board.addProperty("title", title.getString());
        board.add("titleComponent", Segments.raw(title));
        board.add("entries", entries);
        data.add("board", board);

        return data;
    }
}
