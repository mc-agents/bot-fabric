package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.Mc;

/**
 * The attack button, held down by press-input.
 *
 * <p>The client keeps breaking a block only while the button is down and the mouse is grabbed, and
 * the mouse is grabbed only while the window has focus. A container under Xvfb has it; a bot started
 * from a desktop behind another window does not, and there a held left-click started breaking a
 * block and gave up the next tick. A hold made here counts as a grabbed mouse for that one check,
 * through {@code MinecraftMixin}, so what a hold does does not depend on where the client runs.
 *
 * <p>Client thread only: the task that holds it and the tick that reads it both run there.
 */
public final class AttackKey {

    private static boolean held;

    private AttackKey() {
    }

    static void hold() {
        held = true;
        Mc.client().options.keyAttack.setDown(true);
    }

    static void release() {
        held = false;
        Mc.client().options.keyAttack.setDown(false);
    }

    public static boolean held() {
        return held;
    }
}
