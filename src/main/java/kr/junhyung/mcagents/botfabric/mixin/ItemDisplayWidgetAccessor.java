package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.components.ItemDisplayWidget;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The stack an item in a dialog's body draws, and whether hovering it shows the stack's tooltip. */
@Mixin(ItemDisplayWidget.class)
public interface ItemDisplayWidgetAccessor {

    @Accessor("itemStack")
    ItemStack mcagents$itemStack();

    @Accessor("tooltip")
    boolean mcagents$tooltip();
}
