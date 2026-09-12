package kr.junhyung.mcagents.botfabric.tools;

import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Whether the bot can touch a thing without moving first.
 *
 * <p>bot-mineflayer walks to whatever is out of reach. This kind of bot cannot walk yet, so it says
 * how far away the thing is and that it cannot get there, which is a caller's cue to teleport. The
 * alternative is a click the server drops for being too far, and an answer saying it worked.
 *
 * <p>The same three blocks bot-mineflayer uses, measured the same way: feet to block corner, and
 * feet to entity for an entity.
 */
final class Reach {

    private static final double BLOCKS = 3.0;

    private Reach() {
    }

    static void require(Player player, BlockPos at) {
        refuseIfFar(player.position().distanceTo(Vec3.atLowerCornerOf(at)), Positions.point(at));
    }

    static void require(Player player, Entity target) {
        refuseIfFar(player.position().distanceTo(target.position()), Entities.label(target));
    }

    private static void refuseIfFar(double distance, String what) {
        if (distance <= BLOCKS) {
            return;
        }
        throw ToolException.refused("OUT_OF_REACH", what + " is " + Numbers.oneDecimal(distance)
                + " blocks away and this kind of bot cannot walk to it yet."
                + " Teleport to it with run-command first.");
    }
}
