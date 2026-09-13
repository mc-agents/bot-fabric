package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Where a mouse click on a slot goes, rather than the packet it usually becomes.
 *
 * <p>For most windows the two are the same thing. Not for the creative inventory: its slots are an
 * item picker, and on its inventory tab a click is applied to the real inventory locally and sent as
 * the slot's new contents, not as a click. Sending the packet directly there clicked the picker's
 * slot number on the real inventory, which is a different slot, and reported it as done.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenInvoker {

    @Invoker("slotClicked")
    void mcagents$slotClicked(Slot slot, int slotId, int button, ContainerInput input);

    /**
     * What the screen does with a scroll over a slot. A bundle's is the only one there is, and it
     * is added by the screen's init, so a screen that was never initialised has none.
     */
    @Accessor("itemSlotMouseActions")
    List<ItemSlotMouseAction> mcagents$itemSlotMouseActions();
}
