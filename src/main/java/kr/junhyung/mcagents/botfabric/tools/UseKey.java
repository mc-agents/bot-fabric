package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.Mc;
import net.minecraft.client.player.LocalPlayer;

/**
 * The use button, held down for a use that has to last.
 *
 * <p>The client lets go of an item in use on the first tick the button is up, and nobody is at the
 * button: a drawn bow was released a tick after it was drawn and fired nothing, and food was put
 * down after one bite. So a use holds the key, and lets go when it is told to or, for a plain use,
 * when the item is no longer in use -- held any longer, the client would start eating the next one.
 *
 * <p>Client thread only: the tool that holds it and the tick that lets go both run there.
 */
public final class UseKey {

    private static boolean untilUsed;

    private UseKey() {
    }

    static void hold() {
        untilUsed = false;
        Mc.client().options.keyUse.setDown(true);
    }

    /** Held until the item is no longer in use, which for food is when it has been eaten. */
    static void holdUntilUsed() {
        hold();
        untilUsed = true;
    }

    static void release() {
        untilUsed = false;
        Mc.client().options.keyUse.setDown(false);
    }

    /** At the end of every client tick. A use that never began lets go on the tick it was asked for. */
    public static void tick() {
        LocalPlayer player = Mc.client().player;

        if (untilUsed && (player == null || !player.isUsingItem())) {
            release();
        }
    }
}
