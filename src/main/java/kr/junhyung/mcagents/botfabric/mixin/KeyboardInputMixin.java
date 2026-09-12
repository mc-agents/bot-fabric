package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.nav.Steering;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the bot hold a movement key.
 *
 * <p>The tail of this method is the only place it can go. {@code tick} reads the keyboard into
 * {@code keyPresses} and derives {@code moveVector} from it, and the player's travel step runs
 * later in the same tick, so anything set before is overwritten and anything set after arrives a
 * tick late.
 *
 * <p>{@code moveVector} is rebuilt here rather than left alone, because it is what the movement
 * code actually reads: replacing only the key presses moves nothing.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Shadow
    public Input keyPresses;

    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("TAIL"))
    private void botfabric$steer(CallbackInfo info) {
        Input wanted = Steering.pressed();

        if (wanted == null) {
            return;
        }

        keyPresses = wanted;
        moveVector = new Vec2(impulse(wanted.left(), wanted.right()),
                impulse(wanted.backward(), wanted.forward())).normalized();
    }

    private static float impulse(boolean negative, boolean positive) {
        if (negative == positive) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
