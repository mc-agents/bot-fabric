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
    private static volatile boolean sneaking;
    private static volatile boolean sprinting;

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

    /**
     * Sneak and sprint, held apart from the movement keys because they outlast them: a walk that
     * ends releases its keys, and a crouch asked for before it must not end with it.
     */
    public static void sneak(boolean held) {
        sneaking = held;
    }

    public static void sprint(boolean held) {
        sprinting = held;
    }

    /** Everything let go, for a connection that ended: a new world starts standing up. */
    public static void reset() {
        pressed = null;
        sneaking = false;
        sprinting = false;
    }

    /** The keys a tick ends with: the movement keys given, or the keyboard's, with the stance held on top. */
    public static Input over(Input keys) {
        return new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(),
                keys.shift() || sneaking, keys.sprint() || sprinting);
    }
}
