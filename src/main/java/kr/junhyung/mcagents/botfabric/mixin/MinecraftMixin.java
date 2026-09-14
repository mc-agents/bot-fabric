package kr.junhyung.mcagents.botfabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kr.junhyung.mcagents.botfabric.tools.AttackKey;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a held attack keep breaking the block it started on.
 *
 * <p>The keybinding handler goes on breaking only while the mouse is grabbed, which is how a person
 * alt-tabbing away stops digging. A bot's window has focus or not depending on where it runs, and
 * nobody alt-tabs a bot, so only that check is overridden: everything else about the hold is the
 * client's own -- the progress it sends, the block it moves on to, letting go.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @ModifyExpressionValue(method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean botfabric$heldAttackKeepsBreaking(boolean grabbed) {
        return grabbed || AttackKey.held();
    }
}
