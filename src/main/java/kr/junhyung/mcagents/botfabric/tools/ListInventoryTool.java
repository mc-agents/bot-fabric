package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Every stack the bot is carrying, by slot.
 *
 * <p>Read through the player's inventory menu rather than through the inventory itself, because
 * the numbers have to be the ones click-slot takes: 36 is the first hotbar slot in a window and 0
 * is the first hotbar slot in an Inventory. An agent that reads a slot here and clicks it there
 * has to be looking at the same number, and the menu is what defines that numbering.
 */
public final class ListInventoryTool extends ReadTool {

    public ListInventoryTool() {
        super("list-inventory");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        JsonArray items = new JsonArray();

        for (Slot slot : Mc.requirePlayer().inventoryMenu.slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty()) {
                items.add(Items.stack(item, slot.index));
            }
        }

        JsonObject data = new JsonObject();
        data.add("items", items);
        return data;
    }
}
