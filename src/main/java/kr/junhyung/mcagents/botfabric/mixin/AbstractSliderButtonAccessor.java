package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Where a slider's handle is, from nought to one, and moving it the way a drag does.
 *
 * <p>Through the slider's own setter, which is what a drag ends in: it applies the value and redraws
 * the message, so what the dialog's button later sends is read from the slider as it would be after
 * a player moved it.
 */
@Mixin(AbstractSliderButton.class)
public interface AbstractSliderButtonAccessor {
    @Accessor("value")
    double mcagents$value();

    @Invoker("setValue")
    void mcagents$setValue(double value);
}
