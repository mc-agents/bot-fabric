package kr.junhyung.mcagents.botfabric.nav;

import net.minecraft.world.entity.player.Input;

/**
 * Where a held movement key comes from when nobody is at the keyboard.
 *
 * <p>The client builds its {@code Input} from {@link net.minecraft.client.KeyMapping}s once per
 * tick and derives the movement vector from it in the same method, so this is read at the tail of
 * that method by a mixin and nowhere else. Setting the player's velocity instead would move a
 * client the server does not believe: what a server accepts is the player walking under its own
 * rules.
 *
 * <p>Volatile because a task sets it on the client thread and the mixin reads it on the same one,
 * except when a link drops and the RPC reader releases it.
 */
public final class Steering {

    private static volatile Input pressed;

    private Steering() {
    }

    public static void press(Input keys) {
        pressed = keys;
    }

    public static void release() {
        pressed = null;
    }

    /** Null when the keyboard -- or the absence of one -- should win. */
    public static Input pressed() {
        return pressed;
    }
}
