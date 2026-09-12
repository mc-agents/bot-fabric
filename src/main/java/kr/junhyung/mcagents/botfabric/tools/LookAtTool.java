package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Point the bot at a block, which is what a screenshot of that block needs first.
 *
 * <p>At the middle of it rather than its corner, because a corner puts the block at the edge of
 * the frame and the thing being looked at half out of it.
 */
public final class LookAtTool extends ActionTool {

    public LookAtTool() {
        super("look-at");
    }

    @Override
    protected String act(JsonObject args) {
        BlockPos at = Positions.of(args);

        Mc.requirePlayer().lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(at));

        return "Looking at " + Positions.point(at) + ".";
    }
}
