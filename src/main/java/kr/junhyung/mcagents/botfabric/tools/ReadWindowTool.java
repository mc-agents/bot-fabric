package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * What the open window holds, or the fact that there is not one.
 *
 * <p>The shape is the catalogue's {@code resultSchema}, not this class's: mcp-server renders it,
 * and a field named differently here is a field the renderer cannot read. It used to send
 * {@code containerId} and {@code titleSegments}, which the catalogue has never had.
 *
 * <p>Nothing being open is a state, so it travels as {@code window: null} rather than as a refusal
 * with this class's own wording. Comparing the two kinds of bot is what found that they each had
 * one.
 */
public final class ReadWindowTool implements Tool {

    /** A player inventory is 36 slots wherever it is attached. */
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    @Override
    public String name() {
        return "read-window";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Mc.immediate(call, () -> {
            Minecraft minecraft = Mc.client();
            Screen screen = minecraft.screen;

            if (!(screen instanceof AbstractContainerScreen<?> container)) {
                JsonObject empty = new JsonObject();
                empty.add("window", JsonNull.INSTANCE);
                call.ok("no window is open", empty);
                return;
            }

            JsonObject data = new JsonObject();
            data.add("window", describe(container.getMenu(), screen.getTitle().getString()));
            call.ok("window \"%s\"".formatted(screen.getTitle().getString()), data);
        });
    }

    private static JsonObject describe(AbstractContainerMenu menu, String title) {
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

    private static JsonArray range(int from, int to) {
        JsonArray range = new JsonArray();
        range.add(from);
        range.add(to);
        return range;
    }

    private static JsonObject slot(int index, ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("slot", index);
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
}
