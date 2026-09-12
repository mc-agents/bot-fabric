package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the bot has been taught, in the shape the catalogue's recipe DTOs want.
 *
 * <p>A Minecraft client is sent a recipe as the server unlocks it and is never told the whole set,
 * so everything here is about what this bot can place rather than about what the game can make.
 * That is why the three recipe tools send {@code onlyWhatTheBotKnows}: "no recipe" from a client
 * means "not taught to me", and an agent told the wrong one of those goes looking in the wrong
 * place.
 */
final class Recipes {

    /** Anything wider than the player's own grid needs a table, whatever the display calls for. */
    private static final int PLAYER_GRID = 2;

    private static final int TABLE_SEARCH = 8;

    private Recipes() {
    }

    static ContextMap context(LocalPlayer player) {
        return SlotDisplayContext.fromLevel(player.level());
    }

    static List<RecipeDisplayEntry> known(LocalPlayer player) {
        List<RecipeDisplayEntry> entries = new ArrayList<>();

        for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
            entries.addAll(collection.getRecipes());
        }
        return entries;
    }

    /** Every taught recipe that makes the named item. Crafting only: a furnace is smelt-item's. */
    static List<RecipeDisplayEntry> producing(LocalPlayer player, String item, ContextMap context) {
        List<RecipeDisplayEntry> found = new ArrayList<>();

        for (RecipeDisplayEntry entry : known(player)) {
            if (!isCrafting(entry.display())) {
                continue;
            }
            for (ItemStack stack : entry.resultItems(context)) {
                if (path(stack).equals(item)) {
                    found.add(entry);
                    break;
                }
            }
        }
        return found;
    }

    static boolean isCrafting(RecipeDisplay display) {
        return display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay;
    }

    static boolean requiresTable(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > PLAYER_GRID || shaped.height() > PLAYER_GRID;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > PLAYER_GRID * PLAYER_GRID;
        }
        return true;
    }

    static boolean tableInReach(LocalPlayer player) {
        return Positions.nearest(player, Blocks.CRAFTING_TABLE, TABLE_SEARCH) != null;
    }

    /**
     * One line per distinct ingredient, with how many of it the recipe takes.
     *
     * <p>A slot display is a set of things that would do -- any log, any plank -- and the first is
     * the one named, because a caller wants a name to go and get rather than the whole set.
     */
    static Map<String, Integer> ingredients(RecipeDisplayEntry entry, ContextMap context) {
        Map<String, Integer> counted = new LinkedHashMap<>();

        for (SlotDisplay slot : slots(entry.display())) {
            ItemStack stack = slot.resolveForFirstStack(context);
            if (!stack.isEmpty()) {
                counted.merge(path(stack), 1, Integer::sum);
            }
        }
        return counted;
    }

    private static List<SlotDisplay> slots(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.ingredients();
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients();
        }
        return List.of();
    }

    /** What the inventory is short of for one recipe. Empty means it can be made now. */
    static Map<String, Integer> missing(LocalPlayer player, Map<String, Integer> needed) {
        Map<String, Integer> held = new LinkedHashMap<>();

        for (Slot slot : player.inventoryMenu.slots) {
            ItemStack stack = slot.getItem();
            if (slot.index >= 9 && !stack.isEmpty()) {
                held.merge(path(stack), stack.getCount(), Integer::sum);
            }
        }

        Map<String, Integer> shortfall = new LinkedHashMap<>();
        needed.forEach((name, count) -> {
            int gap = count - held.getOrDefault(name, 0);
            if (gap > 0) {
                shortfall.put(name, gap);
            }
        });
        return shortfall;
    }

    static JsonObject describe(LocalPlayer player, RecipeDisplayEntry entry, ContextMap context) {
        Map<String, Integer> needed = ingredients(entry, context);
        ItemStack result = entry.resultItems(context).stream().findFirst().orElse(ItemStack.EMPTY);

        JsonObject recipe = new JsonObject();
        recipe.add("result", stack(path(result), result.getCount()));
        recipe.add("ingredients", counts(needed));
        recipe.add("missing", counts(missing(player, needed)));
        recipe.addProperty("requiresTable", requiresTable(entry.display()));

        return recipe;
    }

    static JsonArray counts(Map<String, Integer> counted) {
        JsonArray array = new JsonArray();
        counted.forEach((name, count) -> array.add(stack(name, count)));
        return array;
    }

    static JsonObject stack(String name, int count) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("count", count);
        return entry;
    }

    static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
