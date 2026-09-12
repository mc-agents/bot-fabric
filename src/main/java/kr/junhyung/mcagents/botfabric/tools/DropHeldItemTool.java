package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Put a stack on the ground: the one the cursor holds, or the one in a named slot.
 *
 * <p>The cursor is emptied by clicking outside the window, which is PICKUP on slot -999, and button
 * 0 there sends the whole stack rather than one item. A named slot is THROW with button 1, which is
 * the ctrl-Q of the vanilla client and needs no trip through the cursor.
 *
 * <p>Nothing to drop is a state, so it travels as {@code dropped: null} and mcp-server says so.
 * With no window open the menu is the player's own inventory, which is where the cursor lives then.
 */
public final class DropHeldItemTool extends ReadTool {

    /** Clicking outside the window. The number is the protocol's, not ours. */
    private static final int OUTSIDE = -999;

    public DropHeldItemTool() {
        super("drop-held-item");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Player player = Mc.requirePlayer();
        AbstractContainerMenu menu = player.containerMenu;
        JsonObject data = new JsonObject();

        JsonElement slot = args.get("slot");
        if (slot == null || slot.isJsonNull()) {
            data.add("slot", JsonNull.INSTANCE);
            data.add("dropped", drop(menu, player, OUTSIDE, 0, menu.getCarried()));
            return data;
        }

        int index = new Args(args).integer("slot", -1);
        if (index < 0 || index >= menu.slots.size()) {
            throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + index
                    + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
        }

        data.addProperty("slot", index);
        data.add("dropped", drop(menu, player, index, 1, menu.getSlot(index).getItem()));

        return data;
    }

    private static JsonElement drop(AbstractContainerMenu menu, Player player, int slot, int button,
            ItemStack stack) {
        JsonElement dropped = Windows.held(stack.copy());

        if (!stack.isEmpty()) {
            ContainerInput input = slot == OUTSIDE ? ContainerInput.PICKUP : ContainerInput.THROW;
            Mc.client().gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
        }

        return dropped;
    }
}
