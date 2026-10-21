package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The dialog a screen was built from.
 *
 * <p>Its inputs are what give the controls on the screen their keys: the checkbox, the cycle and the
 * slider carry a label and nothing of the key their action's template reads.
 *
 * <p>And the scroll area its body is laid out in, which is the only thing that tells the body's
 * widgets from the title above them and the buttons below.
 */
@Mixin(DialogScreen.class)
public interface DialogScreenAccessor {
    @Accessor("dialog")
    Dialog mcagents$dialog();

    @Accessor("bodyScroll")
    ScrollableLayout mcagents$bodyScroll();
}
