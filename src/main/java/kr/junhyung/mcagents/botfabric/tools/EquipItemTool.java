package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Put an item where it can be used: in hand, in the off-hand, or worn.
 *
 * <p>All of it goes through the player's own inventory menu, which is the container the server sees
 * when no other one is open, so these are the same clicks a person makes. Nothing here writes the
 * inventory directly: a client that moved an item without telling the server would be holding
 * something the server does not think it has.
 *
 * <p>SWAP with a button of 0-8 is the number-key swap and 40 is the off-hand key -- the protocol's
 * numbers, not this tool's. Armour is two picks rather than a shift-click, because a shift-click
 * sends whatever is not armour somewhere else entirely and still looks like it worked.
 */
public final class EquipItemTool extends ActionTool {

    /** InventoryMenu's own layout: 5-8 worn, 9-35 the bag, 36-44 the hotbar. */
    private static final int WORN_HEAD = 5;
    private static final int BAG_START = 9;
    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END = 44;
    private static final int OFF_HAND_BUTTON = 40;

    public EquipItemTool() {
        super("equip-item");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        String query = parsed.string("itemName");
        String destination = parsed.string("destination");

        LocalPlayer player = Mc.requirePlayer();
        InventoryMenu menu = player.inventoryMenu;
        int source = find(menu, query);
        String item = BuiltInRegistries.ITEM.getKey(menu.getSlot(source).getItem().getItem()).getPath();

        switch (destination) {
            case "hand" -> toHand(player, menu, source);
            case "off-hand" -> click(player, menu, source, OFF_HAND_BUTTON, ContainerInput.SWAP);
            case "head", "torso", "legs", "feet" -> wear(player, menu, source, worn(destination));
            default -> throw ToolException.badArgs("unknown destination " + destination);
        }

        return "Equipped " + item + " to " + destination + ".";
    }

    private static int find(InventoryMenu menu, String query) {
        for (Slot slot : menu.slots) {
            if (slot.index >= BAG_START && !slot.getItem().isEmpty()
                    && Items.matches(slot.getItem(), query)) {
                return slot.index;
            }
        }
        throw ToolException.refused("NO_SUCH_ITEM", "No inventory item matches \"" + query + "\"");
    }

    private static int worn(String destination) {
        return switch (destination) {
            case "head" -> WORN_HEAD;
            case "torso" -> WORN_HEAD + 1;
            case "legs" -> WORN_HEAD + 2;
            default -> WORN_HEAD + 3;
        };
    }

    /* Already on the hotbar is a held-item change, which is what a person does with a number key. */
    private static void toHand(LocalPlayer player, InventoryMenu menu, int source) {
        if (source >= HOTBAR_START && source <= HOTBAR_END) {
            player.getInventory().setSelectedSlot(source - HOTBAR_START);
            return;
        }
        click(player, menu, source, player.getInventory().getSelectedSlot(), ContainerInput.SWAP);
    }

    /**
     * Pick it up, put it in the armour slot, and put whatever came off back where the first one was.
     * Leaving a stack on the cursor drops it the moment anything else opens a window.
     */
    private static void wear(LocalPlayer player, InventoryMenu menu, int source, int target) {
        if (source == target) {
            return;
        }
        click(player, menu, source, 0, ContainerInput.PICKUP);
        click(player, menu, target, 0, ContainerInput.PICKUP);

        if (!menu.getCarried().isEmpty()) {
            click(player, menu, source, 0, ContainerInput.PICKUP);
        }
    }

    private static void click(LocalPlayer player, InventoryMenu menu, int slot, int button,
            ContainerInput input) {
        Mc.client().gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
    }
}
