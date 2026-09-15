package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.stream.Stream;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Readings;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * What a server is drawing in a display slot, usually the sidebar, read the way the game draws it.
 *
 * <p>A plugin's sidebar is one team per line: the owner is a string of colour codes nobody sees,
 * the words are the team's prefix and suffix, and the number is hidden behind a blank format or
 * replaced with a fixed one. Read off the entries as stored, that board was a column of colour
 * codes with a number after each. So the lines go through what {@code Gui.displayScoreboardSidebar}
 * does: the hidden entries dropped, the rest ordered and cut off at fifteen, the name wrapped in
 * its team and the number put through the objective's format. The list and below-name slots are
 * drawn without teams, so those keep the plain owner, though their number is formatted the same way.
 */
public final class ReadScoreboardTool extends ReadTool {

    private static final int SIDEBAR_LINES = 15;

    private static final Comparator<PlayerScoreEntry> DRAWN_ORDER =
            Comparator.comparing(PlayerScoreEntry::value).reversed()
                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    public ReadScoreboardTool() {
        super("read-scoreboard");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String wanted = args.has("slot") ? args.get("slot").getAsString() : "sidebar";
        /*
        The catalogue spells the slot the way the protocol document's first bot did, and the game
        spells it below_name. Looking "belowName" up as it is found nothing, so a below-name
        objective always read as no scoreboard.
        */
        DisplaySlot slot = DisplaySlot.CODEC.byName("belowName".equals(wanted) ? "below_name" : wanted);

        JsonObject data = new JsonObject();
        data.addProperty("slot", wanted);

        LocalPlayer player = Mc.requirePlayerEvenIfDead();
        Scoreboard scoreboard = player.level().getScoreboard();
        Objective objective = slot == null ? null : displayed(scoreboard, slot, player);

        /* Nothing displayed is a state, not a failure, so the server says the words. */
        if (objective == null) {
            data.add("board", JsonNull.INSTANCE);
            return data;
        }

        boolean sidebar = slot == DisplaySlot.SIDEBAR;
        NumberFormat format = objective.numberFormatOrDefault(defaultFormat(slot));
        Stream<PlayerScoreEntry> scores = scoreboard.listPlayerScores(objective).stream().sorted(DRAWN_ORDER);
        if (sidebar) {
            scores = scores.filter(score -> !score.isHidden()).limit(SIDEBAR_LINES);
        }

        JsonArray entries = new JsonArray();
        scores.forEach(score -> {
            /*
            ownerName() and not owner(): a server may give an entry a name of its own, and that is
            what the sidebar draws. Reading the owner instead reported the key -- a uuid or an
            internal handle on the servers that use one -- where the screen showed a player's name.
            */
            Component name = sidebar
                    ? PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), score.ownerName())
                    : score.ownerName();
            Component value = score.formatValue(format);

            JsonObject entry = new JsonObject();
            entry.addProperty("name", Readings.readable(name.getString()));
            entry.add("nameComponent", Segments.raw(name));
            entry.addProperty("score", score.value());
            entry.addProperty("scoreText", Readings.readable(value.getString()));
            entry.add("scoreComponent", Segments.raw(value));
            entries.add(entry);
        });

        Component title = objective.getDisplayName();

        JsonObject board = new JsonObject();
        board.addProperty("title", title.getString());
        board.add("titleComponent", Segments.raw(title));
        board.add("entries", entries);
        data.add("board", board);

        return data;
    }

    /**
     * The objective the slot draws. The sidebar has one per team colour beside the plain one, and
     * the game draws the one for the bot's own team when it is on a team and that slot is filled.
     */
    private static Objective displayed(Scoreboard scoreboard, DisplaySlot slot, LocalPlayer player) {
        if (slot == DisplaySlot.SIDEBAR) {
            PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
            DisplaySlot teamSlot = team == null ? null : DisplaySlot.teamColorToSlot(team.getColor());
            Objective teamObjective = teamSlot == null ? null : scoreboard.getDisplayObjective(teamSlot);
            if (teamObjective != null) {
                return teamObjective;
            }
        }
        return scoreboard.getDisplayObjective(slot);
    }

    /** Each slot draws an unformatted number in its own colour, and the sidebar's is red. */
    private static NumberFormat defaultFormat(DisplaySlot slot) {
        return switch (slot) {
            case SIDEBAR -> StyledFormat.SIDEBAR_DEFAULT;
            case LIST -> StyledFormat.PLAYER_LIST_DEFAULT;
            default -> StyledFormat.NO_STYLE;
        };
    }
}
