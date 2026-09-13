package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** What block is at a position, for checking that a build or a command did what it said. */
public final class GetBlockInfoTool extends ReadTool {

    public GetBlockInfoTool() {
        super("get-block-info");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        BlockPos at = Positions.of(args);
        BlockState state = Mc.requirePlayerEvenIfDead().level().getBlockState(at);

        JsonObject block = new JsonObject();
        block.addProperty("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        /* The numeric id the protocol carries, which is what a version-independent check compares. */
        block.addProperty("type", BuiltInRegistries.BLOCK.getId(state.getBlock()));
        block.add("position", Positions.json(at));

        JsonObject data = new JsonObject();
        data.add("position", Positions.json(at));
        data.add("block", block);
        return data;
    }
}
