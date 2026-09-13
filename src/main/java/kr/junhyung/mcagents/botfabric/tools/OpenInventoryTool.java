package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.CreativeModeInventoryScreenInvoker;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Open the bot's own inventory, which is what pressing E does.
 *
 * <p>Nothing opened it before, so the slots a player reaches only there were out of reach: the
 * two-by-two crafting grid, the armour, the offhand, and moving things around inside the inventory
 * with the swaps and drags click-slot and drag-slots already know.
 *
 * <p>What E opens depends on the player, and this opens the same. Riding something with an
 * inventory of its own, the server decides and is asked. In creative the client shows the creative
 * inventory instead of the survival one -- the survival screen replaces itself as it opens -- and a
 * player picks a tab there; this picks the one asked for, or the inventory tab, which is the one
 * whose slots are the inventory.
 */
public final class OpenInventoryTool extends ActionTool {

    private static final String LAYOUT = "Slots 0-45: 0 crafting result, 1-4 crafting grid, 5-8 armour,"
            + " 9-35 main inventory, 36-44 hotbar, 45 offhand.";

    public OpenInventoryTool() {
        super("open-inventory");
    }

    @Override
    protected String act(JsonObject args) {
        String wanted = new Args(args).string("tab", null);
        LocalPlayer player = Mc.requirePlayer();

        if (Mc.screen() != null) {
            throw ToolException.refused("SCREEN_OPEN", Mc.screen().getClass().getSimpleName() + " is open, and E"
                    + " closes a window rather than opening the inventory over it. Use close-window first.");
        }

        if (Mc.client().gameMode.isServerControlledInventory()) {
            player.sendOpenInventory();
            return "Asked the server for the inventory, because what the bot is riding decides which one."
                    + " Use wait-for-window, then read-window.";
        }

        /* The same test the survival screen makes as it opens, before deciding to become the creative one. */
        if (!player.hasInfiniteMaterials()) {
            if (wanted != null) {
                throw ToolException.refused("NOT_CREATIVE", "tabs belong to the creative inventory, and the bot is"
                        + " not in creative, so there is no \"" + wanted + "\" to open");
            }
            Mc.setScreen(new InventoryScreen(player));
            return "Opened the inventory. " + LAYOUT;
        }

        Mc.setScreen(new InventoryScreen(player));

        if (!(Mc.screen() instanceof CreativeModeInventoryScreen creative)) {
            throw ToolException.refused("NO_CREATIVE_INVENTORY", "the bot is in creative but "
                    + (Mc.screen() == null ? "nothing" : Mc.screen().getClass().getSimpleName())
                    + " opened in place of the creative inventory");
        }

        CreativeModeTab tab = wanted == null ? inventoryTab() : pick(wanted);
        ((CreativeModeInventoryScreenInvoker) creative).mcagents$selectTab(tab);

        String name = tab.getDisplayName().getString();

        if (tab.getType() == CreativeModeTab.Type.INVENTORY) {
            return "Opened the creative inventory on its \"" + name + "\" tab. " + LAYOUT
                    + " Clicks here change the inventory the way a creative player's do.";
        }
        return "Opened the creative inventory on its \"" + name + "\" tab. Its first slots are the items the"
                + " tab offers, and the last nine are the hotbar; read-window shows which is which."
                + " Clicking an offered item takes a stack of it onto the cursor.";
    }

    private static CreativeModeTab inventoryTab() {
        return CreativeModeTabs.allTabs().stream()
                .filter(tab -> tab.getType() == CreativeModeTab.Type.INVENTORY)
                .findFirst()
                .orElseThrow(() -> ToolException.refused("NO_INVENTORY_TAB", "the creative inventory has no inventory tab"));
    }

    private static CreativeModeTab pick(String wanted) {
        List<CreativeModeTab> offered = CreativeModeTabs.tabs();
        String lowered = wanted.toLowerCase(Locale.ROOT);

        for (CreativeModeTab tab : offered) {
            if (tab.getDisplayName().getString().equalsIgnoreCase(wanted)) {
                return tab;
            }
        }
        for (CreativeModeTab tab : offered) {
            if (tab.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(lowered)) {
                return tab;
            }
        }
        throw ToolException.refused("NO_SUCH_TAB", "the creative inventory has no tab called \"" + wanted
                + "\". It has " + offered.stream()
                .map(tab -> "\"" + tab.getDisplayName().getString() + "\"")
                .collect(Collectors.joining(", ")) + ".");
    }
}
