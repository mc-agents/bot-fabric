package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The item an item display holds, as the server synced it rather than as the renderer last saw it. */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayInvoker {

    @Invoker("getItemStack")
    ItemStack mcagents$itemStack();
}
