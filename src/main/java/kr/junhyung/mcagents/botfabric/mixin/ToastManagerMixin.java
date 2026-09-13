package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.event.Feeds;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A toast as it is put up, not as it is drawn.
 *
 * <p>Five seconds on screen and gone, and only five fit at once -- the rest wait in a queue the
 * manager does not show. Reading the visible list would miss a toast that came and went between
 * two calls and every one still queued behind it. Every toast passes through here once.
 *
 * <p>Only an advancement is sent from here. A recipe toast gathers every recipe unlocked while it
 * is up into the one toast, so only the first would pass this point; that one is caught where the
 * recipe is added instead. The rest -- tutorial hints, the client's own system notices -- are the
 * client talking to itself, and nothing a server can make appear.
 */
@Mixin(ToastManager.class)
public class ToastManagerMixin {

    @Inject(method = "addToast", at = @At("HEAD"))
    private void botfabric$toast(Toast toast, CallbackInfo info) {
        if (toast instanceof AdvancementToast advancement) {
            Feeds.advancement(((AdvancementToastAccessor) advancement).mcagents$advancement());
        }
    }
}
