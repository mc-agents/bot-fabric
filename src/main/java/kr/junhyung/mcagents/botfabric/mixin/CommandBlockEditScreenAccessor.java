package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The editor's Done button, which is the one sign that the server's copy of the block has arrived.
 *
 * <p>The editor opens before the block's command is sent and stays disabled until it is; the arrival
 * then writes that command over the field. Nothing else on the screen says which side of it the
 * editor is on.
 */
@Mixin(AbstractCommandBlockEditScreen.class)
public interface CommandBlockEditScreenAccessor {

    @Accessor("doneButton")
    Button mcagents$doneButton();
}
