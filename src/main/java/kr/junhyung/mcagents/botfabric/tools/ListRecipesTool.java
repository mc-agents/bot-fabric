package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.Comparator;
import java.util.List;

/**
 * What the bot could make right now, or every taught recipe for one item.
 *
 * <p>Without an item this is the scan, and the scan is capped: a bot carrying a full inventory can
 * reach a great many recipes, and an answer nobody can read is worse than one that says where it
 * stopped.
 */
public final class ListRecipesTool extends ReadTool {

    /** The same cap bot-mineflayer applies, so a capped answer is capped at the same place. */
    private static final int MAX_LISTED = 100;

    public ListRecipesTool() {
        super("list-recipes");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        LocalPlayer player = Mc.requirePlayer();
        ContextMap context = Recipes.context(player);

        boolean named = args.get("outputItem") != null && !args.get("outputItem").isJsonNull();
        String item = named ? Items.plain(new Args(args).string("outputItem")) : null;

        boolean tableInReach = Recipes.tableInReach(player);

        JsonObject data = new JsonObject();
        data.addProperty("tableInReach", tableInReach);
        data.addProperty("onlyWhatTheBotKnows", true);
        data.add("item", item == null ? JsonNull.INSTANCE : new JsonPrimitive(item));

        if (item != null) {
            JsonArray recipes = new JsonArray();
            Recipes.producing(player, item, context).stream()
                    .sorted(Comparator.comparingInt(entry ->
                            Recipes.missing(player, Recipes.ingredients(entry, context)).size()))
                    .forEach(entry -> recipes.add(Recipes.describe(player, entry, context)));

            data.add("stoppedAt", JsonNull.INSTANCE);
            data.add("recipes", recipes);
            return data;
        }

        /*
        "Craftable right now" means right now: a recipe that needs a table the bot cannot reach is
        not one of them, and the other kind of bot leaves those out for the same reason.
        */
        JsonArray craftable = new JsonArray();

        for (RecipeDisplayEntry entry : Recipes.known(player)) {
            if (craftable.size() >= MAX_LISTED) {
                break;
            }
            if (!Recipes.isCrafting(entry.display())) {
                continue;
            }
            if (Recipes.requiresTable(entry.display()) && !tableInReach) {
                continue;
            }
            if (Recipes.missing(player, Recipes.ingredients(entry, context)).isEmpty()) {
                craftable.add(Recipes.describe(player, entry, context));
            }
        }

        data.add("stoppedAt", craftable.size() >= MAX_LISTED ? new JsonPrimitive(MAX_LISTED) : JsonNull.INSTANCE);
        data.add("recipes", craftable);

        return data;
    }
}
