package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Where a kind of block is, nearest first.
 *
 * <p>A cube around the bot rather than a sphere, walked outwards so the first hits are the closest
 * ones. The client only has the chunks it has been sent, so what is outside them is not "absent"
 * -- it is not loaded, and the count stopping short is the only sign of that.
 */
public final class FindBlocksTool extends ReadTool {

    public FindBlocksTool() {
        super("find-blocks");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String wanted = Items.plain(args.get("blockType").getAsString());
        int radius = (int) Math.ceil(args.get("maxDistance").getAsDouble());
        int count = args.get("count").getAsInt();

        Block block = BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(wanted));

        Level level = Mc.requirePlayer().level();
        BlockPos from = Mc.requirePlayer().blockPosition();

        JsonArray positions = new JsonArray();
        for (BlockPos at : BlockPos.withinManhattan(from, radius, radius, radius)) {
            if (level.getBlockState(at).is(block)) {
                positions.add(Positions.json(at));
                if (positions.size() >= count) {
                    break;
                }
            }
        }

        JsonObject data = new JsonObject();
        /* The filter as the caller wrote it, namespace and all. Echoing the stripped form made
           the answer say diamond_block where the caller had asked for minecraft:diamond_block. */
        data.add("blockType", args.get("blockType"));
        data.addProperty("maxDistance", args.get("maxDistance").getAsDouble());
        data.add("positions", positions);
        return data;
    }
}
