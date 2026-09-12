package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
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
        return entry;
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

    static String plain(String id) {
        String trimmed = id.trim().toLowerCase();
        return trimmed.startsWith("minecraft:") ? trimmed.substring("minecraft:".length()) : trimmed;
    }
}
