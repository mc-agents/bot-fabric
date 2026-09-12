package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
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

        Scoreboard scoreboard = Mc.requirePlayer().level().getScoreboard();
        Objective objective = slot == null ? null : scoreboard.getDisplayObjective(slot);

        /* Nothing displayed is a state, not a failure, so the server says the words. */
        if (objective == null) {
            data.add("board", JsonNull.INSTANCE);
            return data;
        }

        JsonArray entries = new JsonArray();
        for (PlayerScoreEntry score : scoreboard.listPlayerScores(objective)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", score.owner());
            entry.addProperty("score", score.value());
            entries.add(entry);
        }

        JsonObject board = new JsonObject();
        board.addProperty("title", objective.getDisplayName().getString());
        board.add("entries", entries);
        data.add("board", board);

        return data;
    }
}
