package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Where a scroll over a bundle ends up once the wheel has been turned into an index.
 *
 * <p>The scroll itself is no way in: it steps one item per notch from wherever the selection is,
 * through a wheel handler that keeps fractions between calls, so reaching item five is a count of
 * notches that depends on what was selected before. This is the call each notch makes, with the
 * index it arrived at. It changes the bundle on this side as well as sending, so a read straight
 * after sees the selection the server now has.
 */
@Mixin(BundleMouseActions.class)
public interface BundleMouseActionsInvoker {

    @Invoker("toggleSelectedBundleItem")
    void mcagents$toggleSelectedBundleItem(ItemStack bundle, int slotId, int index);
}
