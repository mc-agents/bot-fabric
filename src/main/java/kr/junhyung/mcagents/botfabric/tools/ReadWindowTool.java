package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.rpc.CallContext;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.Tool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class ReadWindowTool implements Tool {
    @Override
    public String name() {
        return "read-window";
    }

    @Override
    public String argsHash() {
        return "sha256:d746974fa9afd5e951f76f9af38954b0ad7f436f2120dc974da65e5ee39f856f";
    }

    @Override
    public void invoke(CallContext call, JsonObject args) {
        Mc.immediate(call, () -> {
            Minecraft minecraft = Mc.client();
            Screen screen = minecraft.screen;
            if (!(screen instanceof AbstractContainerScreen<?> container)) {
                throw ToolException.refused("NO_WINDOW", "no GUI window is open");
            }
            AbstractContainerMenu menu = container.getMenu();

            JsonArray slots = new JsonArray();
            for (Slot slot : menu.slots) {
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                slots.add(describe(slot.index, stack));
            }

            JsonObject data = new JsonObject();
            data.addProperty("containerId", menu.containerId);
            data.addProperty("title", screen.getTitle().getString());
            data.add("titleSegments", Segments.of(screen.getTitle()));
            data.addProperty("slotCount", menu.slots.size());
            data.add("slots", slots);

            call.ok("%s: %d of %d slots filled"
                    .formatted(screen.getTitle().getString(), slots.size(), menu.slots.size()), data);
        });
    }

    private static JsonObject describe(int index, ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("slot", index);
        entry.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        entry.addProperty("count", stack.getCount());
        entry.addProperty("name", stack.getHoverName().getString());
        entry.add("nameSegments", Segments.of(stack.getHoverName()));

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            JsonArray lines = new JsonArray();
            lore.lines().forEach(line -> lines.add(line.getString()));
            entry.add("lore", lines);
        }
        return entry;
    }
}
