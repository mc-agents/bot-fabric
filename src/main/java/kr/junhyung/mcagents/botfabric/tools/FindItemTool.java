package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The first carried stack answering to a name, or the fact that there is none. */
public final class FindItemTool extends ReadTool {

    public FindItemTool() {
        super("find-item");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String query = args.get("nameOrType").getAsString();

        JsonObject data = new JsonObject();
        data.addProperty("query", query);

        for (Slot slot : Mc.requirePlayerEvenIfDead().inventoryMenu.slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty() && Items.matches(item, query)) {
                data.add("item", Items.stack(item, slot.index));
                return data;
            }
        }

        /* Not found is a state, not a failure: a check often wants to know a thing is absent. */
        data.add("item", JsonNull.INSTANCE);
        return data;
    }
}
