package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Every trade on the open trading screen.
 *
 * <p>read-window cannot answer this: the trades are not in any slot. They arrive in a packet of
 * their own a moment after the window opens, and the client keeps them on the menu's merchant, so a
 * screen read in the same tick it opened lists none -- which is a state worth reporting as such
 * rather than as a villager with nothing to sell.
 */
public final class ReadTradesTool extends ReadTool {

    public ReadTradesTool() {
        super("read-trades");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        MerchantScreen screen = Trades.require();
        MerchantMenu menu = screen.getMenu();
        MerchantOffers offers = menu.getOffers();

        JsonObject data = new JsonObject();
        data.addProperty("title", screen.getTitle().getString());
        data.add("titleComponent", Segments.raw(screen.getTitle()));

        /*
        A wandering trader is sent a level too, and the screen hides it because the progress bar is
        off. Passing both on lets the renderer do what the screen does instead of naming a level
        nobody sees.
        */
        data.addProperty("level", menu.getTraderLevel());
        data.addProperty("xp", menu.getTraderXp());
        data.addProperty("showProgressBar", menu.showProgressBar());
        data.addProperty("canRestock", menu.canRestock());

        JsonArray trades = new JsonArray();
        for (int index = 0; index < offers.size(); index++) {
            trades.add(Trades.describe(offers.get(index), index + 1));
        }
        data.add("trades", trades);

        return data;
    }
}
