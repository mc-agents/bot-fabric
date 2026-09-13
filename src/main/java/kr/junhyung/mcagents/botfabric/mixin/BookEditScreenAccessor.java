package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Which page of a book is being written, and the two steps between pages.
 *
 * <p>The editor holds one page at a time in its text box, so writing a second page means turning to
 * it first. The buttons that do are drawn as arrows with no label, which leaves nothing for a
 * caller to name; going through the screen's own methods also keeps the behaviour that turning
 * forward past the last page adds one, which is how a book grows.
 */
@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {

    @Accessor("pages")
    List<String> mcagents$pages();

    @Accessor("currentPage")
    int mcagents$currentPage();

    @Invoker("pageForward")
    void mcagents$pageForward();

    @Invoker("pageBack")
    void mcagents$pageBack();
}
