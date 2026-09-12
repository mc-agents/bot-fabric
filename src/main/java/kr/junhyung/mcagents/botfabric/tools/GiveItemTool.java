package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Put an item straight into the inventory, which is what lets a check start from the state it needs
 * rather than gather its way there.
 *
 * <p>Creative only, because the packet behind it is the creative inventory packet and a server in
 * any other mode drops it on the floor of the log. Saying so is better than a silent no-op.
 */
public final class GiveItemTool extends ActionTool {

    private static final int BAG_START = 9;
    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END = 44;

    public GiveItemTool() {
        super("give-item");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        String itemName = parsed.string("itemName");
        int count = parsed.integer("count", 1);

        LocalPlayer player = Mc.requirePlayer();
        String mode = Mc.client().gameMode.getPlayerMode().getName();

        if (!player.getAbilities().instabuild) {
            throw ToolException.refused("NOT_CREATIVE",
                    "The bot is in " + mode + " mode; give-item needs creative.");
        }

        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(Items.namespaced(itemName)))
                .orElseThrow(() -> ToolException.refused("NO_SUCH_ITEM",
                        "\"" + itemName + "\" is not an item in this version."));

        int slot = args.get("slot") == null || args.get("slot").isJsonNull()
                ? firstEmpty(player)
                : parsed.integer("slot", -1);

        ItemStack stack = new ItemStack(item, count);

        /*
        The client has to put it there itself. A creative inventory change is the one place the
        server trusts the client and sends nothing back, so sending only the packet left the server
        holding a pickaxe the bot could not see -- list-inventory read empty and equip-item could
        not find what had just been given.
        */
        player.inventoryMenu.getSlot(slot).set(stack.copy());
        Mc.client().gameMode.handleCreativeModeItemAdd(stack, slot);

        return "Put " + count + " " + itemName + " in slot " + slot + ".";
    }

    /* The hotbar first, the bag after: the other kind of bot fills the same slot for the same call. */
    private static int firstEmpty(LocalPlayer player) {
        for (int slot = HOTBAR_START; slot <= HOTBAR_END; slot++) {
            if (player.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        for (int slot = BAG_START; slot < HOTBAR_START; slot++) {
            if (player.inventoryMenu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        throw ToolException.refused("INVENTORY_FULL", "The inventory is full and no slot was given.");
    }
}
