package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The trade DTO, which read-trades lists and select-trade answers with one of.
 *
 * <p>One builder for both, for the reason {@link Windows} is one: the server renders a trade from
 * one record, and a field the two tools spelt differently would be a field it silently never read.
 */
final class Trades {

    /** The merchant menu's own layout: two payment slots, then the result, then the inventory. */
    static final int PAYMENT_A = 0;
    static final int PAYMENT_B = 1;
    static final int RESULT = 2;

    private Trades() {
    }

    static MerchantScreen require() {
        Screen screen = Mc.screen();

        if (screen instanceof MerchantScreen merchant) {
            return merchant;
        }
        throw ToolException.refused("NO_TRADES", screen == null
                ? "no trading screen is open. Use interact-entity on a villager or a wandering trader, "
                        + "then wait-for-window."
                : screen.getClass().getSimpleName() + " is open, and it is not a trading screen.");
    }

    static JsonObject describe(MerchantOffer offer, int number) {
        JsonObject trade = new JsonObject();
        trade.addProperty("number", number);

        /*
        getCostA is the price with demand and the player's reputation applied, which is what the
        payment slot will actually take. The base count goes beside it so a discount -- or a price
        pushed up by demand -- can be told from a trade that simply costs that much.
        */
        trade.add("costA", Windows.held(offer.getCostA()));
        trade.addProperty("baseCountA", offer.getItemCostA().count());
        trade.add("costB", Windows.held(offer.getCostB()));
        trade.add("result", Windows.held(offer.getResult()));
        trade.add("enchantments", enchantments(offer.getResult()));
        trade.addProperty("uses", offer.getUses());
        trade.addProperty("maxUses", offer.getMaxUses());
        trade.addProperty("outOfStock", offer.isOutOfStock());
        trade.addProperty("xp", offer.getXp());

        return trade;
    }

    /**
     * A librarian sells a dozen enchanted books that read "enchanted_book x1" alike; which book is
     * the entire trade. A book stores them apart from what an enchanted tool carries, so both.
     */
    private static JsonArray enchantments(ItemStack stack) {
        JsonArray list = new JsonArray();

        for (ItemEnchantments carried : new ItemEnchantments[] {
                stack.get(DataComponents.STORED_ENCHANTMENTS), stack.get(DataComponents.ENCHANTMENTS)}) {
            if (carried == null) {
                continue;
            }
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : carried.entrySet()) {
                JsonObject enchantment = new JsonObject();
                enchantment.addProperty("name", entry.getKey().unwrapKey()
                        .map(key -> key.identifier().getPath())
                        .orElse("unknown"));
                enchantment.addProperty("level", entry.getIntValue());
                list.add(enchantment);
            }
        }
        return list;
    }
}
