package net.mcagents.botfabric.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.mcagents.botfabric.render.FrameBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void botfabric$applyBudget(CallbackInfoReturnable<Integer> info) {
        info.setReturnValue(FrameBudget.current());
    }
}
