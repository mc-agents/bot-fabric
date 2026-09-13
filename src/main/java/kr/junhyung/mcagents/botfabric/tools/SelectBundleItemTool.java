package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.mixin.AbstractContainerScreenInvoker;
import kr.junhyung.mcagents.botfabric.mixin.BundleMouseActionsInvoker;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Choose which item a bundle in the open window gives up next, as scrolling over its tooltip does.
 *
 * <p>Choosing takes nothing out. A right click on the bundle with an empty cursor takes out the one
 * chosen, and without a choice it takes out the first. The choice is also easy to lose: a left
 * click, a shift-click or a number key on the bundle clears it, on the server as well as here.
 *
 * <p>The answer lists what is in the bundle, because read-window names the bundle and not its
 * contents, and a caller has to see the numbers to pick by one.
 */
public final class SelectBundleItemTool extends ActionTool {

    public SelectBundleItemTool() {
        super("select-bundle-item");
    }

    @Override
    protected String act(JsonObject args) {
        Args parsed = new Args(args);
        int slot = parsed.integer("slot", -1);
        String wanted = parsed.string("item").strip();

        AbstractContainerScreen<?> screen = Windows.require();
        AbstractContainerMenu menu = screen.getMenu();

        if (slot < 0 || slot >= menu.slots.size()) {
            throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + slot
                    + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
        }

        Slot target = menu.getSlot(slot);
        ItemStack bundle = target.getItem();
        BundleMouseActions scroll = scroll(screen, target);
        BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        List<ItemStack> items = contents.itemCopyStream().toList();

        if (items.isEmpty()) {
            throw ToolException.refused("EMPTY_BUNDLE", "the bundle in slot " + slot + " is empty");
        }

        int index = pick(items, wanted, slot);
        String named = describe(items.get(index), index);

        /*
        The tooltip draws only so many, and the client will not send an index past what it draws. The
        server would take it, but no player can scroll to it, so it is refused rather than sent.
        */
        if (index >= contents.getNumberOfItemsToShow()) {
            throw ToolException.refused("NOT_SHOWN", named + " is past the " + contents.getNumberOfItemsToShow()
                    + " items the bundle's tooltip shows, so it cannot be chosen; take out some of those first");
        }

        /* Choosing the chosen item again is how a selection is cleared, so it is not sent twice. */
        if (contents.getSelectedItemIndex() == index) {
            return named + " is already selected in the bundle in slot " + slot + ". " + contents(items, index);
        }

        ((BundleMouseActionsInvoker) scroll).mcagents$toggleSelectedBundleItem(bundle, slot, index);

        return "Selected " + named + " in the bundle in slot " + slot + ". A right click on slot " + slot
                + " with an empty cursor takes it out. " + contents(items, index);
    }

    /**
     * The screen's own scroll handler for that slot, so a screen that handles bundles differently,
     * or not at all, is refused rather than sent a selection it would never have sent.
     */
    private static BundleMouseActions scroll(AbstractContainerScreen<?> screen, Slot target) {
        if (target.getItem().isEmpty()) {
            throw ToolException.refused("NOT_A_BUNDLE", "slot " + target.index + " is empty");
        }
        for (ItemSlotMouseAction action : ((AbstractContainerScreenInvoker) screen).mcagents$itemSlotMouseActions()) {
            if (action instanceof BundleMouseActions bundle && bundle.matches(target)) {
                return bundle;
            }
        }
        throw ToolException.refused("NOT_A_BUNDLE", "slot " + target.index + " holds "
                + Items.name(target.getItem()) + ", which is not a bundle this window lets a scroll choose from");
    }

    /**
     * A number is the item's place in the bundle, counting from 1. Anything else names the item,
     * exactly first, and a name two stacks answer to is refused: a bundle holds several stacks of
     * one item as often as not, and the first is not necessarily the one that was meant.
     */
    private static int pick(List<ItemStack> items, String wanted, int slot) {
        if (wanted.matches("[0-9]{1,3}")) {
            int number = Integer.parseInt(wanted);
            if (number < 1 || number > items.size()) {
                throw ToolException.refused("NO_SUCH_ITEM", "there is no item " + number + " in the bundle in slot "
                        + slot + ". " + contents(items, -1));
            }
            return number - 1;
        }

        String plain = Items.plain(wanted);
        List<Integer> exact = new ArrayList<>();
        List<Integer> partial = new ArrayList<>();

        for (int index = 0; index < items.size(); index++) {
            ItemStack item = items.get(index);
            if (Items.name(item).equals(plain) || item.getHoverName().getString().equalsIgnoreCase(wanted)) {
                exact.add(index);
            } else if (Items.matches(item, wanted)) {
                partial.add(index);
            }
        }

        List<Integer> found = exact.isEmpty() ? partial : exact;
        if (found.size() == 1) {
            return found.getFirst();
        }
        if (found.isEmpty()) {
            throw ToolException.refused("NO_SUCH_ITEM", "nothing in the bundle in slot " + slot + " matches \""
                    + wanted + "\". " + contents(items, -1));
        }
        throw ToolException.refused("AMBIGUOUS_ITEM", "\"" + wanted + "\" matches "
                + found.stream().map(index -> describe(items.get(index), index)).collect(Collectors.joining(", "))
                + " in the bundle in slot " + slot + "; pick one by its number");
    }

    private static String contents(List<ItemStack> items, int selected) {
        return "It holds " + IntStream.range(0, items.size())
                .mapToObj(index -> describe(items.get(index), index) + (index == selected ? " (selected)" : ""))
                .collect(Collectors.joining(", ")) + ".";
    }

    private static String describe(ItemStack item, int index) {
        String label = item.has(DataComponents.CUSTOM_NAME) ? " \"" + item.getHoverName().getString() + "\"" : "";
        return (index + 1) + ". " + Items.name(item) + label + " x" + item.getCount();
    }
}
