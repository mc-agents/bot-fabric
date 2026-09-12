package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.Comparator;
import java.util.List;

/**
 * Every taught recipe for one item, with what the inventory is still short of.
 *
 * <p>{@code stoppedAt} is always null: only the unrestricted scan in list-recipes can run out of
 * room, and this one is asked about a single item.
 */
public final class GetRecipeTool extends ReadTool {

    public GetRecipeTool() {
        super("get-recipe");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String item = Items.plain(new Args(args).string("itemName"));
        LocalPlayer player = Mc.requirePlayer();
        ContextMap context = Recipes.context(player);

        List<RecipeDisplayEntry> found = Recipes.producing(player, item, context);
        JsonArray recipes = new JsonArray();

        found.stream()
                .sorted(Comparator.comparingInt(entry ->
                        Recipes.missing(player, Recipes.ingredients(entry, context)).size()))
                .forEach(entry -> recipes.add(Recipes.describe(player, entry, context)));

        JsonObject data = new JsonObject();
        data.addProperty("item", item);
        data.addProperty("tableInReach", Recipes.tableInReach(player));
        data.add("stoppedAt", JsonNull.INSTANCE);
        data.add("recipes", recipes);
        data.addProperty("onlyWhatTheBotKnows", true);

        return data;
    }
}
