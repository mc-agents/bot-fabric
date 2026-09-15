package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.CatalogHashes;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Drag the cursor's stack across several slots, which is QUICK_CRAFT and not a run of clicks.
 *
 * <p>A drag is a small state machine on both sides of the connection: a start outside the window,
 * one packet per slot, and an end outside the window again. The button carries both halves at once,
 * the phase in the low two bits and the kind of drag in the two above them, which is
 * {@link AbstractContainerMenu#getQuickcraftMask}. Getting either half wrong is not an error
 * anywhere: the menu resets its drag and the items never move.
 *
 * <p>All of it goes in one call, the way the client sends it on mouse release. Any other click
 * between the start and the end resets the drag on the server, so splitting it across calls would
 * let an agent's read-window in the middle quietly throw the whole thing away.
 *
 * <p>Answered once the server has sent the window back after the end, through {@link ServerResync}:
 * only the end moves the state id, so the start and the slots go through as the client sends them.
 */
public final class DragSlotsTool implements Tool {

    /** Outside the window, where a drag starts and ends. The number is the protocol's. */
    private static final int OUTSIDE = -999;

    private static final int START = 0;
    private static final int ADD_SLOT = 1;
    private static final int END = 2;

    @Override
    public String name() {
        return "drag-slots";
    }

    @Override
    public String argsHash() {
        return CatalogHashes.of(name());
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Mc.immediate(call, () -> drag(call, args));
    }

    private void drag(CallContext call, JsonObject args) {
        String button = new Args(args).string("button");
        int kind = switch (button) {
            case "left" -> 0;
            case "right" -> 1;
            case "middle" -> 2;
            default -> throw ToolException.badArgs("unknown button " + button);
        };

        AbstractContainerScreen<?> container = Windows.require();
        AbstractContainerMenu menu = container.getMenu();
        Mc.requirePlayer();
        List<Integer> slots = slots(args, menu);

        List<ItemStack> before = new ArrayList<>(slots.size());
        for (int slot : slots) {
            before.add(menu.getSlot(slot).getItem().copy());
        }
        ItemStack carried = menu.getCarried().copy();

        Windows.click(container, OUTSIDE, AbstractContainerMenu.getQuickcraftMask(START, kind), ContainerInput.QUICK_CRAFT);
        for (int slot : slots) {
            Windows.click(container, slot, AbstractContainerMenu.getQuickcraftMask(ADD_SLOT, kind), ContainerInput.QUICK_CRAFT);
        }
        ServerResync.click(ServerResync.Reply.of(call, name()), container,
                () -> Windows.click(container, OUTSIDE, AbstractContainerMenu.getQuickcraftMask(END, kind), ContainerInput.QUICK_CRAFT),
                window -> dragged(menu, slots, before, carried, button, window));
    }

    private static JsonObject dragged(AbstractContainerMenu menu, List<Integer> slots, List<ItemStack> before,
            ItemStack carried, String button, JsonElement window) {
        /* A window the server replaced is gone, and what its slots held is the client's guess. */
        boolean replaced = !window.isJsonNull();
        JsonArray results = new JsonArray();
        for (int i = 0; i < slots.size(); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slots.get(i));
            entry.add("before", Windows.held(before.get(i)));
            entry.add("after", replaced ? JsonNull.INSTANCE : Windows.held(menu.getSlot(slots.get(i)).getItem()));
            results.add(entry);
        }

        JsonObject data = new JsonObject();
        data.addProperty("button", button);
        data.add("slots", results);
        data.add("carried", Windows.held(carried));
        data.add("cursor", Windows.held((replaced ? Mc.requirePlayer().containerMenu : menu).getCarried()));
        data.add("window", window);

        return data;
    }

    /**
     * A slot named twice is refused because the menu keeps a set: the second mention would be
     * ignored there and still show up here as a slot the drag reached twice.
     */
    private static List<Integer> slots(JsonObject args, AbstractContainerMenu menu) {
        JsonElement given = args.get("slots");
        if (given == null || !given.isJsonArray() || given.getAsJsonArray().isEmpty()) {
            throw ToolException.badArgs("expected a non-empty array of slots");
        }

        List<Integer> slots = new ArrayList<>();
        for (JsonElement element : given.getAsJsonArray()) {
            int slot;
            try {
                slot = element.getAsInt();
            } catch (RuntimeException e) {
                throw ToolException.badArgs("expected an integer in slots, got " + element);
            }
            if (slot < 0 || slot >= menu.slots.size()) {
                throw ToolException.refused("SLOT_OUT_OF_RANGE", "Slot " + slot
                        + " is outside the window, whose slots run 0-" + (menu.slots.size() - 1));
            }
            if (slots.contains(slot)) {
                throw ToolException.badArgs("slot " + slot + " is listed twice");
            }
            slots.add(slot);
        }
        return slots;
    }
}
