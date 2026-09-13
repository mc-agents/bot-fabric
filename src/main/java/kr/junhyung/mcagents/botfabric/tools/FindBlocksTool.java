package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Where a kind of block is, nearest first.
 *
 * <p>Nearest means nearest, which is not the order the search runs in. Walking outwards by
 * Manhattan distance reaches a block two across before one five away and three down, so taking the
 * first hits it meets answered a request for the three closest diamond blocks of a nine-block patch
 * with a block six away while leaving out one five away. The other kind of bot over-fetches and
 * sorts for the same reason; a comment there said this one sorted, and it did not.
 *
 * <p>The walk is still outwards, because that is what lets it stop early: a block whose Manhattan
 * distance is more than root-three times the worst one kept cannot be closer than that one,
 * however it is laid out. So the whole cube is only ever read when there are not enough blocks in
 * it to fill the count.
 *
 * <p>The client only has the chunks it has been sent, so what is outside them is not "absent" --
 * it is not loaded, and the count stopping short is the only sign of that.
 */
public final class FindBlocksTool extends ReadTool {

    /** The most a Euclidean distance can fall short of a Manhattan one, in three dimensions. */
    private static final double SQRT_3 = Math.sqrt(3);

    public FindBlocksTool() {
        super("find-blocks");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String wanted = Items.plain(args.get("blockType").getAsString());
        double maxDistance = args.get("maxDistance").getAsDouble();
        int radius = (int) Math.ceil(maxDistance);
        int count = args.get("count").getAsInt();

        Block block = BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(wanted));

        Level level = Mc.requirePlayerEvenIfDead().level();
        BlockPos from = Mc.requirePlayerEvenIfDead().blockPosition();

        JsonArray positions = new JsonArray();
        for (BlockPos at : nearest(level, block, from, maxDistance, radius, count)) {
            positions.add(Positions.json(at));
        }

        JsonObject data = new JsonObject();
        /* The filter as the caller wrote it, namespace and all. Echoing the stripped form made
           the answer say diamond_block where the caller had asked for minecraft:diamond_block. */
        data.add("blockType", args.get("blockType"));
        data.addProperty("maxDistance", maxDistance);
        data.add("positions", positions);
        return data;
    }

    /**
     * The closest {@code count} blocks of that kind, in order.
     *
     * <p>Distance is measured between block positions rather than to the middle of a block, and ties
     * are broken by coordinate, because both of those are what the other kind of bot does and the
     * two have to answer with the same list.
     */
    private static List<BlockPos> nearest(Level level, Block block, BlockPos from,
                                          double maxDistance, int radius, int count) {
        Comparator<BlockPos> byDistance = Comparator
                .comparingDouble((BlockPos at) -> at.distSqr(from))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ);

        /* Largest first, so the one dropped when the count is full is the farthest kept. */
        PriorityQueue<BlockPos> kept = new PriorityQueue<>(byDistance.reversed());
        double worst = Double.MAX_VALUE;

        for (BlockPos at : BlockPos.withinManhattan(from, radius, radius, radius)) {
            if (kept.size() >= count && at.distManhattan(from) > worst * SQRT_3) {
                break;
            }
            if (at.distSqr(from) > maxDistance * maxDistance || !level.getBlockState(at).is(block)) {
                continue;
            }

            kept.add(at.immutable());
            if (kept.size() > count) {
                kept.poll();
            }
            if (kept.size() >= count) {
                worst = Math.sqrt(kept.peek().distSqr(from));
            }
        }

        List<BlockPos> found = new ArrayList<>(kept);
        found.sort(byDistance);

        return found;
    }
}
