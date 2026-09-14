package kr.junhyung.mcagents.botfabric.mixin;

import kr.junhyung.mcagents.botfabric.nav.Steering;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
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
 *
 * <p>Sneak and sprint are laid over whatever the keys are, held or not. The key presses are what
 * the client tells the server and what {@code isShiftKeyDown} reads, so a crouch set anywhere else
 * -- the entity's shared flag -- is undone here on the next tick and never reaches the server.
 *
 * <p>Extending {@link ClientInput} is how the two fields are reached. They are declared there and
 * not on the target, and {@code @Shadow} only looks at the target class -- which the client says at
 * the moment it applies the mixin, by refusing to load and dropping the connection.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void botfabric$steer(CallbackInfo info) {
        Input wanted = Steering.pressed();

        if (wanted != null) {
            this.moveVector = new Vec2(impulse(wanted.left(), wanted.right()),
                    impulse(wanted.backward(), wanted.forward())).normalized();
        }
        this.keyPresses = Steering.over(wanted == null ? this.keyPresses : wanted);
    }

    private static float impulse(boolean negative, boolean positive) {
        if (negative == positive) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
