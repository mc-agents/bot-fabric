package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The window DTOs, in the shape the catalogue's {@code resultSchema} decides.
 *
 * <p>Four tools send one of these and mcp-server renders all four, so a field named differently in
 * any one of them is a field the renderer cannot read. Building them in one place is what stops the
 * four from drifting apart: {@code read-window} once sent {@code containerId} and
 * {@code titleSegments}, which the catalogue has never had.
 */
final class Windows {

    /** A player inventory is 36 slots wherever it is attached. */
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private Windows() {
    }

    /** The open container screen, or null. Nothing being open is a state and not a failure. */
    static AbstractContainerScreen<?> open() {
        return Mc.client().screen instanceof AbstractContainerScreen<?> container ? container : null;
    }

    static AbstractContainerScreen<?> require() {
        AbstractContainerScreen<?> container = open();
        if (container == null) {
            throw ToolException.refused("NO_WINDOW",
                    "No window is open. Run the command that opens the menu first, "
                            + "then use wait-for-window before reading or clicking it.");
        }
        return container;
    }

    static JsonObject describe(AbstractContainerScreen<?> container) {
        return describe(container.getMenu(), container.getTitle().getString());
    }

    static JsonObject describe(AbstractContainerMenu menu, String title) {
        JsonArray filled = new JsonArray();
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                filled.add(slot(slot.index, stack));
            }
        }

        int slotCount = menu.slots.size();
        int inventoryStart = Math.max(slotCount - PLAYER_INVENTORY_SLOTS, 0);

        JsonObject window = new JsonObject();
        window.addProperty("title", title);
        window.addProperty("type", BuiltInRegistries.MENU.getKey(menu.getType()).toString());
        window.addProperty("slotCount", slotCount);
        window.add("containerSlots", range(0, Math.max(inventoryStart - 1, 0)));
        window.add("inventorySlots", range(inventoryStart, Math.max(slotCount - 1, 0)));
        window.add("filled", filled);

        return window;
    }

    /** A stack in a numbered slot. Only ever called for one that holds something. */
    static JsonObject slot(int index, ItemStack stack) {
        JsonObject entry = stack(stack);
        entry.addProperty("slot", index);
        return entry;
    }

    /**
     * A stack that is not in a numbered slot: under the cursor, or on its way to the ground. Empty
     * travels as no stack at all rather than as a count of zero.
     */
    static JsonElement held(ItemStack stack) {
        return stack == null || stack.isEmpty() ? JsonNull.INSTANCE : stack(stack);
    }

    private static JsonObject stack(ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        entry.addProperty("count", stack.getCount());

        /* A label is the custom name a server gave it; the plain item keeps none. */
        boolean named = stack.has(DataComponents.CUSTOM_NAME);
        entry.addProperty("label", named ? stack.getHoverName().getString() : null);

        JsonArray lore = new JsonArray();
        ItemLore lines = stack.get(DataComponents.LORE);
        if (lines != null) {
            lines.lines().forEach(line -> lore.add(line.getString()));
        }
        entry.add("lore", lore);

        return entry;
    }

    private static JsonArray range(int from, int to) {
        JsonArray range = new JsonArray();
        range.add(from);
        range.add(to);
        return range;
    }
}
