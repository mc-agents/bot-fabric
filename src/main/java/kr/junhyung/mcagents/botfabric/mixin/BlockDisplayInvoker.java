package kr.junhyung.mcagents.botfabric.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The block a block display draws, as the server synced it. The render state beside it is only
 * filled in when a frame is drawn, and a client that draws few frames can be behind.
 */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayInvoker {

    @Invoker("getBlockState")
    BlockState mcagents$blockState();
}
