package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * What pressing a trade in the list does, without pressing it.
 *
 * <p>The seven buttons are only a window onto the list: each one picks its own index plus how far
 * the list is scrolled, and they stay invisible until a frame has drawn them, which a bot with its
 * frames budgeted away may never do. So the tool sets the index the button would have set and runs
 * the same method it runs. Sending the packet alone would leave the payment slots empty on this
 * side until the server's copy arrived, and the result slot would read empty for a trade that
 * worked.
 */
@Mixin(MerchantScreen.class)
public interface MerchantScreenInvoker {

    @Accessor("shopItem")
    void mcagents$setShopItem(int index);

    @Invoker("postButtonClick")
    void mcagents$postButtonClick();
}
