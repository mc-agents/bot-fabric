package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The pages of the book that is open, and which one is showing.
 *
 * <p>A book on screen is drawn one page at a time from a record the screen keeps to itself, and
 * nothing public hands it back. Reading a book by turning to each page and screenshotting it is not
 * reading it, so this is what makes a quest log the server handed over something an agent can read
 * in one call. A lectern shows the same screen, which is why both arrive here.
 */
@Mixin(BookViewScreen.class)
public interface BookViewScreenAccessor {

    @Accessor("bookAccess")
    BookViewScreen.BookAccess mcagents$bookAccess();

    @Accessor("currentPage")
    int mcagents$currentPage();
}
