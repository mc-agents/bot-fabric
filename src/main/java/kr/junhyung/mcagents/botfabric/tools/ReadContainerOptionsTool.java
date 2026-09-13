package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * What the open menu offers to press: the part of an enchanting table, a stonecutter, a loom or a
 * lectern that is drawn rather than kept in a slot.
 *
 * <p>Not part of read-window. That DTO is built once in {@link Windows} and shared by four tools so
 * that they cannot drift apart, and every one of them describes slots; a lectern has no container
 * screen at all, so read-window reports nothing open while this has a book to page through.
 */
public final class ReadContainerOptionsTool extends ReadTool {

    public ReadContainerOptionsTool() {
        super("read-container-options");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        JsonObject data = new JsonObject();
        AbstractContainerMenu menu = ContainerOptions.open();

        data.add("window", menu == null ? JsonNull.INSTANCE : ContainerOptions.describe(menu));

        return data;
    }
}
