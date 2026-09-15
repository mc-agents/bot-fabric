package kr.junhyung.mcagents.botfabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kr.junhyung.mcagents.botfabric.render.Cursor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a tool put the cursor over a slot for the frames it takes.
 *
 * <p>The GUI is extracted with the mouse's scaled position, truncated to the integers every screen
 * gets as mouseX and mouseY. Only that read is overridden, so hovering, the tooltip and where it is
 * placed are all the screen's own; the mouse handler itself is never moved, so a cleared override
 * is the real cursor again on the next frame.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyExpressionValue(method = "extractGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D"))
    private double botfabric$cursorX(double real) {
        return Cursor.x(real);
    }

    @ModifyExpressionValue(method = "extractGui",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D"))
    private double botfabric$cursorY(double real) {
        return Cursor.y(real);
    }
}
