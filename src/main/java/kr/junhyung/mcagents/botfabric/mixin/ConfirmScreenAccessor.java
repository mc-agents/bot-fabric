package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The button that says yes.
 *
 * <p>A confirmation offers more than two things to click and the affirmative one is not first among
 * them: running a command from a dialog opens "Confirm Command Execution", whose widgets begin with
 * "Copy to Chat Screen". Pressing that copies the command into the chat box and runs nothing, so
 * press-dialog-button reported having pressed a button whose action never happened.
 *
 * <p>The field is what the screen was built with, so this holds whatever that confirmation calls
 * yes without this mod having to know the wording.
 */
@Mixin(ConfirmScreen.class)
public interface ConfirmScreenAccessor {
    @Accessor("yesButton")
    Button mcagents$yesButton();
}
