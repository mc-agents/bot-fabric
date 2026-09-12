package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Click one slot of the open window.
 *
 * <p>{@code ClickType} is gone in 26.x and {@link ContainerInput} took its place, so a click is
 * {@code handleContainerInput} with that enum rather than with a mode number. PICKUP with button 0
 * or 1 is a plain left or right click; QUICK_MOVE is the shift-click, which the server resolves and
 * whose result never passes through the cursor.
 *
 * <p>The slot after the click and the cursor go back with the answer. A plugin that cancels the
 * click leaves both untouched, and a sentence naming only the slot cannot tell that from a click
 * that worked.
 */
public final class ClickSlotTool extends ReadTool {

    public ClickSlotTool() {
        super("click-slot");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Args parsed = new Args(args);
        int slot = parsed.integer("slot", -1);
        String button = parsed.string("button");
        boolean shift = parsed.bool("shift", false);

        AbstractContainerScreen<?> container = Windows.require();
        AbstractContainerMenu menu = container.getMenu();

        if (slot < 0 || slot >= menu.slots.size()) {
            throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + slot
                    + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
        }

        ItemStack before = menu.getSlot(slot).getItem().copy();

        Mc.client().gameMode.handleContainerInput(menu.containerId, slot,
                "right".equals(button) ? 1 : 0,
                shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP,
                Mc.requirePlayer());

        JsonObject data = new JsonObject();
        data.addProperty("slot", slot);
        data.addProperty("button", button);
        data.addProperty("shift", shift);
        data.add("before", Windows.held(before));
        data.add("after", Windows.held(menu.getSlot(slot).getItem()));
        data.add("cursor", Windows.held(menu.getCarried()));

        return data;
    }
}
