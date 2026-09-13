package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What a sign being edited currently says, and which of its lines has the cursor.
 *
 * <p>The sign editor is the one screen with text on it that is not a widget: the four lines live in
 * a private array and the caret in a private int, and nothing on Screen exposes either. Without
 * these, typing into a sign could be done but not reported, and clearing a line would mean sending
 * backspaces until they stopped having an effect.
 */
@Mixin(AbstractSignEditScreen.class)
public interface SignEditScreenAccessor {

    @Accessor("messages")
    String[] mcagents$messages();

    @Accessor("line")
    int mcagents$line();

    /** Which face is being written, which depends on the side the sign was clicked from. */
    @Accessor("isFrontText")
    boolean mcagents$isFrontText();
}
