package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Point the bot at a block, which is what a screenshot of that block needs first.
 *
 * <p>At the middle of it rather than its corner, because a corner puts the block at the edge of
 * the frame and the thing being looked at half out of it.
 */
public final class LookAtTool extends ReadTool {

    public LookAtTool() {
        super("look-at");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        BlockPos at = Positions.of(args);
        Vec3 middle = new Vec3(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);

        Mc.requirePlayer().lookAt(EntityAnchorArgument.Anchor.EYES, middle);

        return new JsonObject();
    }

    @Override
    protected String summary(JsonObject data) {
        return "looking";
    }
}
