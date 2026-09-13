package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.event.Feeds;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each recipe a server unlocks with a toast, one line apiece.
 *
 * <p>The toast itself is shared: a second recipe unlocked while the first is up is added to the
 * same toast and never passes through the manager again. Counting toasts would say one recipe
 * where the server unlocked six.
 */
@Mixin(RecipeToast.class)
public class RecipeToastMixin {

    @Inject(method = "addOrUpdate", at = @At("HEAD"))
    private static void botfabric$recipe(ToastManager manager, RecipeDisplay display, CallbackInfo info) {
        Feeds.recipe(display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(manager.getMinecraft().level)));
    }
}
