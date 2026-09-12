package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.nav.DirectNavigator;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Getting close enough to touch something, one tick at a time.
 *
 * <p>Every tool that acts on a thing at a distance walks to it first, the way bot-mineflayer does,
 * and that is why those tools are tasks: a walk takes ticks. A task holds one of these and asks it
 * at the top of each tick whether it has arrived yet.
 *
 * <p>The three blocks are bot-mineflayer's, measured the same way: feet to block corner, and feet to
 * entity for an entity. Asking again before each swing is also its behaviour, and it is what keeps a
 * bot on a mob that backs away.
 */
final class Approach {

    private static final double REACH = 3.0;

    private final DirectNavigator navigator = new DirectNavigator();

    boolean reached(LocalPlayer player, BlockPos at) {
        return reached(player, Vec3.atLowerCornerOf(at), Positions.point(at));
    }

    boolean reached(LocalPlayer player, Entity target) {
        return reached(player, target.position(), Entities.label(target));
    }

    void stop() {
        navigator.stop();
    }

    private boolean reached(LocalPlayer player, Vec3 target, String what) {
        if (navigator.step(player, target, REACH)) {
            return true;
        }
        if (navigator.stuck(player)) {
            navigator.stop();
            throw ToolException.refused("UNREACHABLE", "could not get within reach of " + what
                    + "; it stopped " + Numbers.oneDecimal(player.position().distanceTo(target))
                    + " blocks away. This kind of bot walks straight at a target and does not go"
                    + " around walls, so teleport with run-command when something is in the way.");
        }
        return false;
    }
}
