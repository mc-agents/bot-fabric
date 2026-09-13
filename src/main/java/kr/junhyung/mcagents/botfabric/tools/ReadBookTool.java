package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.mixin.BookViewScreenAccessor;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Read the book that is open, every page of it.
 *
 * <p>A server that has something long to say says it in a book: a quest log, a rulebook, a guide
 * handed out on join. The client draws one page at a time, so without this the only way to read one
 * was a screenshot per page -- and the pages carry components, which a screenshot cannot give back.
 *
 * <p>A lectern shows the same screen, so a book on a stand reads the same way as one in hand.
 */
public final class ReadBookTool extends ReadTool {

    public ReadBookTool() {
        super("read-book");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        Screen screen = Mc.screen();

        if (!(screen instanceof BookViewScreen view)) {
            throw ToolException.refused("NO_BOOK", screen == null
                    ? "no book is open. Use a written book, or right-click a lectern with one on it."
                    : screen.getClass().getSimpleName() + " is open, and it is not a book.");
        }

        BookViewScreen.BookAccess access = ((BookViewScreenAccessor) view).mcagents$bookAccess();
        JsonObject data = new JsonObject();

        data.addProperty("source", screen instanceof LecternScreen ? "lectern" : "hand");
        data.addProperty("page", ((BookViewScreenAccessor) view).mcagents$currentPage() + 1);

        JsonArray pages = new JsonArray();
        JsonArray components = new JsonArray();

        for (int at = 0; at < access.getPageCount(); at++) {
            Component page = access.getPage(at);
            pages.add(page.getString());
            components.add(Segments.raw(page));
        }
        data.add("pages", pages);
        data.add("pageComponents", components);

        describe(data, written(screen));

        return data;
    }

    /**
     * Title and author, which the screen does not hold: it is handed the pages alone. A lectern
     * knows the stack it is showing, and a book being read from the hand is the one being held.
     */
    private static WrittenBookContent written(Screen screen) {
        if (screen instanceof LecternScreen lectern) {
            return lectern.getMenu().getBook().get(DataComponents.WRITTEN_BOOK_CONTENT);
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = Mc.requirePlayer().getItemInHand(hand);
            WrittenBookContent content = held.get(DataComponents.WRITTEN_BOOK_CONTENT);

            if (content != null) {
                return content;
            }
        }
        return null;
    }

    private static void describe(JsonObject data, WrittenBookContent content) {
        if (content == null) {
            data.add("title", JsonNull.INSTANCE);
            data.add("author", JsonNull.INSTANCE);
            data.add("generation", JsonNull.INSTANCE);
            return;
        }

        data.addProperty("title", content.title().raw());
        data.addProperty("author", content.author());
        data.addProperty("generation", content.generation());
    }
}
