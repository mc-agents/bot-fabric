package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** One stack, as the catalogue describes it: the registry path, how many, and where. */
final class Items {

    private Items() {
    }

    static JsonObject stack(ItemStack item, int slot) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", BuiltInRegistries.ITEM.getKey(item.getItem()).getPath());
        entry.addProperty("count", item.getCount());
        entry.addProperty("slot", slot);

        /*
        The name a server gave it, and the component that name was written as. A quest item or a
        menu button is named in the pack's own font, and a list that only said "paper x1" could
        not tell one from another.
        */
        boolean named = item.has(DataComponents.CUSTOM_NAME);
        entry.addProperty("label", named ? item.getHoverName().getString() : null);
        entry.add("labelComponent",
                named ? Segments.raw(item.get(DataComponents.CUSTOM_NAME)) : JsonNull.INSTANCE);

        return entry;
    }

    /** The registry path, which is how a sentence names an item: "iron_ingot", not "Iron Ingot". */
    static String name(ItemStack item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
    }

    /**
     * Whether a stack answers to what a caller typed. The namespace is dropped first, because
     * "minecraft:diamond" and "diamond" are the same thing to somebody looking for a diamond.
     */
    static boolean matches(ItemStack item, String query) {
        String needle = plain(query);
        String name = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();

        return name.contains(needle) || item.getHoverName().getString().toLowerCase().contains(needle);
    }

    /** The canonical form, for a registry lookup: a caller may or may not have written the namespace. */
    static String namespaced(String id) {
        String trimmed = id.trim().toLowerCase();
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    static String plain(String id) {
        String trimmed = id.trim().toLowerCase();
        return trimmed.startsWith("minecraft:") ? trimmed.substring("minecraft:".length()) : trimmed;
    }
}
