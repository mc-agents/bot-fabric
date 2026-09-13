package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Click one slot of the open window, or press over it one of the keys a player presses there.
 *
 * <p>{@code ClickType} is gone in 26.x and {@link ContainerInput} took its place, so a click is
 * {@code handleContainerInput} with that enum rather than with a mode number. PICKUP with button 0
 * or 1 is a plain left or right click; QUICK_MOVE is the shift-click, which the server resolves and
 * whose result never passes through the cursor.
 *
 * <p>The other inputs carry something else in the button, and what it carries is the menu's
 * business rather than the mouse's. SWAP takes an {@link Inventory} index, not the key's number and
 * not a window slot: the "1" key is index 0 and the offhand is 40, and anything else is dropped by
 * the menu without a word. THROW takes 0 for one item and 1 for the stack. PICKUP_ALL scans the
 * window forwards on 0; the client only ever sends that. CLONE ignores the button.
 *
 * <p>The slot after the click and the cursor go back with the answer. A plugin that cancels the
 * click leaves both untouched, and a sentence naming only the slot cannot tell that from a click
 * that worked. A swap moves a second stack that may not be in the window at all -- the offhand
 * never is -- so that one is read off the inventory and goes back as well.
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
        String mode = parsed.string("mode", "click");
        int hotbar = parsed.integer("hotbar", 0);

        AbstractContainerScreen<?> container = Windows.require();
        AbstractContainerMenu menu = container.getMenu();
        Player player = Mc.requirePlayer();

        if (slot < 0 || slot >= menu.slots.size()) {
            throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + slot
                    + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
        }

        Input input = Input.of(mode, button, shift, hotbar);
        int swapIndex = input.type() == ContainerInput.SWAP ? input.button() : -1;

        ItemStack before = menu.getSlot(slot).getItem().copy();
        ItemStack swappedBefore = swapIndex < 0 ? null : player.getInventory().getItem(swapIndex).copy();

        Mc.client().gameMode.handleContainerInput(menu.containerId, slot, input.button(), input.type(), player);

        JsonObject data = new JsonObject();
        data.addProperty("slot", slot);
        data.addProperty("button", button);
        data.addProperty("shift", shift);
        data.addProperty("mode", mode);
        data.addProperty("hotbar", hotbar == 0 ? null : hotbar);
        data.add("before", Windows.held(before));
        data.add("after", Windows.held(menu.getSlot(slot).getItem()));
        data.add("cursor", Windows.held(menu.getCarried()));

        if (swapIndex < 0) {
            data.add("swapped", JsonNull.INSTANCE);
        } else {
            JsonObject swapped = new JsonObject();
            swapped.add("before", Windows.held(swappedBefore));
            swapped.add("after", Windows.held(player.getInventory().getItem(swapIndex)));
            data.add("swapped", swapped);
        }

        return data;
    }

    private record Input(ContainerInput type, int button) {

        /**
         * A shift or a right button given to anything but a click is refused rather than dropped.
         * Every one of them would still send something, and it would be a different input from the
         * one that was asked for.
         */
        static Input of(String mode, String button, boolean shift, int hotbar) {
            if (!"click".equals(mode) && (shift || !"left".equals(button))) {
                throw ToolException.badArgs("button and shift shape a click, and " + mode + " takes neither");
            }
            if ("swap-hotbar".equals(mode) != (hotbar != 0)) {
                throw ToolException.badArgs("hotbar names the key for swap-hotbar, and only swap-hotbar takes one");
            }

            return switch (mode) {
                case "click" -> new Input(shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP,
                        "right".equals(button) ? 1 : 0);
                case "swap-hotbar" -> new Input(ContainerInput.SWAP, hotbar - 1);
                case "swap-offhand" -> new Input(ContainerInput.SWAP, Inventory.SLOT_OFFHAND);
                case "throw-one" -> new Input(ContainerInput.THROW, 0);
                case "throw-stack" -> new Input(ContainerInput.THROW, 1);
                case "pickup-all" -> new Input(ContainerInput.PICKUP_ALL, 0);
                case "clone" -> new Input(ContainerInput.CLONE, 0);
                default -> throw ToolException.badArgs("unknown mode " + mode);
            };
        }
    }
}
