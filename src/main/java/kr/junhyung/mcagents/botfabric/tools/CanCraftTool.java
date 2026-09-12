package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Whether the bot could make one of something right now, and what is stopping it.
 *
 * <p>The answer is about this bot: a client is sent a recipe as the server unlocks it, so a recipe
 * the bot has not been taught is one it cannot place, whatever the game can make. The DTO says which
 * kind of answer it is, and mcp-server writes the sentence that names the difference.
 */
public final class CanCraftTool extends ReadTool {

    public CanCraftTool() {
        super("can-craft");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String item = Items.plain(new Args(args).string("itemName"));
        LocalPlayer player = Mc.requirePlayer();
        ContextMap context = Recipes.context(player);

        List<RecipeDisplayEntry> recipes = Recipes.producing(player, item, context);

        JsonObject data = new JsonObject();
        data.addProperty("item", item);
        data.addProperty("onlyWhatTheBotKnows", true);

        if (recipes.isEmpty()) {
            data.addProperty("craftable", false);
            data.addProperty("hasRecipe", false);
            data.add("missing", Recipes.counts(Map.of()));
            data.addProperty("needsTable", false);
            return data;
        }

        /* The one that needs the least, which is what a caller is told to go and get. */
        boolean tableInReach = Recipes.tableInReach(player);
        RecipeDisplayEntry closest = recipes.stream()
                .min(Comparator.comparingInt(entry ->
                        Recipes.missing(player, Recipes.ingredients(entry, context)).size()))
                .orElseThrow();

        Map<String, Integer> missing = Recipes.missing(player, Recipes.ingredients(closest, context));
        boolean needsTable = Recipes.requiresTable(closest.display()) && !tableInReach;

        data.addProperty("craftable", missing.isEmpty() && !needsTable);
        data.addProperty("hasRecipe", true);
        data.add("missing", Recipes.counts(missing));
        data.addProperty("needsTable", needsTable);

        return data;
    }
}
