package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.mixin.MerchantScreenInvoker;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Pick a trade on the open trading screen, the way pressing it in the list does.
 *
 * <p>Picking is not trading. The client moves the price out of the inventory into the payment slots
 * and works out the result slot from them in the same call, so the slots are read straight after:
 * an empty result then says whether the trade is out of stock or the inventory could not pay, and
 * a sentence naming only the trade could say neither. The trade itself is taking slot 2 with
 * click-slot.
 *
 * <p>A DTO rather than a sentence, though it changes something, for the reason click-slot sends
 * one: what lands in the result slot is an item a server may have named in its own font, and only
 * the server's renderer keeps that.
 */
public final class SelectTradeTool extends ReadTool {

    public SelectTradeTool() {
        super("select-trade");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String query = new Args(args).string("trade").trim();

        MerchantScreen screen = Trades.require();
        MerchantMenu menu = screen.getMenu();
        MerchantOffers offers = menu.getOffers();

        if (offers.isEmpty()) {
            throw ToolException.refused("NO_TRADES_YET", "the trading screen lists no trades yet. "
                    + "They arrive a moment after it opens: wait a few ticks and read-trades.");
        }

        int index = pick(offers, query);
        MerchantScreenInvoker button = (MerchantScreenInvoker) screen;
        button.mcagents$setShopItem(index);
        button.mcagents$postButtonClick();

        JsonObject data = new JsonObject();
        data.add("trade", Trades.describe(offers.get(index), index + 1));
        data.add("paymentA", Windows.held(menu.getSlot(Trades.PAYMENT_A).getItem()));
        data.add("paymentB", Windows.held(menu.getSlot(Trades.PAYMENT_B).getItem()));
        data.add("result", Windows.held(menu.getSlot(Trades.RESULT).getItem()));

        return data;
    }

    /**
     * A number is the trade's place in the list. Anything else names what the trade gives, exactly
     * first: a villager that buys wheat, potatoes and carrots gives emeralds for all three, and
     * "emerald" picking whichever came first would be a trade nobody asked for.
     */
    private static int pick(MerchantOffers offers, String query) {
        if (query.matches("[0-9]{1,3}")) {
            int number = Integer.parseInt(query);

            if (number < 1 || number > offers.size()) {
                throw ToolException.refused("NO_SUCH_TRADE", "there is no trade " + number
                        + "; this screen lists " + offers.size() + ", numbered from 1.");
            }
            return number - 1;
        }

        List<Integer> exact = new ArrayList<>();
        List<Integer> partial = new ArrayList<>();
        String plain = Items.plain(query);

        for (int index = 0; index < offers.size(); index++) {
            ItemStack result = offers.get(index).getResult();

            if (BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().equals(plain)
                    || result.getHoverName().getString().equalsIgnoreCase(query)) {
                exact.add(index);
            } else if (Items.matches(result, query)) {
                partial.add(index);
            }
        }

        List<Integer> found = exact.isEmpty() ? partial : exact;

        if (found.size() == 1) {
            return found.getFirst();
        }
        if (found.isEmpty()) {
            throw ToolException.refused("NO_SUCH_TRADE", "no trade gives \"" + query + "\". This screen sells "
                    + list(offers, IntStream.range(0, offers.size()).boxed().toList()) + ".");
        }
        throw ToolException.refused("AMBIGUOUS_TRADE", found.size() + " trades give \"" + query
                + "\": " + list(offers, found) + ". Pick one by its number.");
    }

    private static String list(MerchantOffers offers, List<Integer> indices) {
        return indices.stream()
                .map(index -> (index + 1) + ". " + summary(offers.get(index)))
                .collect(Collectors.joining(", "));
    }

    private static String summary(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        return result.getHoverName().getString() + " x" + result.getCount();
    }
}
