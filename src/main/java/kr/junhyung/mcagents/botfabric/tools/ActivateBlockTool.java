package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ActionTool;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click a block: a button, a lever, a door, or whatever a plugin put there.
 *
 * <p>What opens a window is this followed by wait-for-window, and that pair is the general form of
 * open-container. Nothing is waited for here, because a lever has no window and waiting for one
 * would make every button press cost a timeout.
 */
public final class ActivateBlockTool extends ActionTool {

    public ActivateBlockTool() {
        super("activate-block");
    }

    @Override
    protected String act(JsonObject args) {
        BlockPos at = Positions.of(args);
        LocalPlayer player = Mc.requirePlayer();

        if (!player.level().isLoaded(at)) {
            throw ToolException.refused("NOT_LOADED", Positions.point(at)
                    + " is outside the loaded chunks, so there is no block to activate");
        }

        Reach.require(player, at);

        String block = BuiltInRegistries.BLOCK
                .getKey(player.level().getBlockState(at).getBlock()).getPath();
        Vec3 middle = Vec3.atCenterOf(at);

        player.lookAt(EntityAnchorArgument.Anchor.EYES, middle);
        Mc.client().gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(middle, Direction.UP, at, false));
        player.swing(InteractionHand.MAIN_HAND);

        return "Right-clicked " + block + " at " + Positions.point(at) + ".";
    }
}
