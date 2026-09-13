package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Switching the creative inventory's tab, which a player does by clicking a drawn icon. */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenInvoker {

    @Invoker("selectTab")
    void mcagents$selectTab(CreativeModeTab tab);
}
