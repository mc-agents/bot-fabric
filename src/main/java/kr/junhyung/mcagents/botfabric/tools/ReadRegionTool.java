package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.Args;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every block in a box, as a palette and the runs that spell it out.
 *
 * <p>A build is checked by reading it back, and reading a wall back a block at a time is a thousand
 * calls. The palette and the run lengths are what keep a whole region down to one answer somebody
 * can read: a cube of stone is one run, and a patterned floor is a handful.
 *
 * <p>The walk is y, then z, then x, all ascending, so a run is a row along x and a flat layer stays
 * contiguous. The order is part of the answer -- nothing else says where a run sits -- so the other
 * kind of bot walks it the same way, and a region read by one is the same runs read by the other.
 *
 * <p>Three things end a run rather than continuing it, and two of them are counted rather than
 * reported as blocks. A y past the top or bottom of the world holds nothing to read at all, which
 * is {@code outside}; within the world, a position whose chunk the client was never sent is
 * {@code missing}. They are kept apart because the answer to one is to fly closer and the answer to
 * the other is to ask for a box inside the world, and nothing should add them together. Air dropped
 * by {@code includeAir} ends a run too: two rows of stone with a gap between them stay two runs,
 * because joining them would claim a solid span that is not there.
 *
 * <p>How much of a box is {@code missing} is a fact about this bot and not about the world. A real
 * client holds the chunks the server sent it and no more, so a box that a headless bot reads whole
 * comes back part-missing here whenever it reaches past the render distance. The invariants hold on
 * both; the number does not, and comparing the two kinds of bot on it means nothing.
 */
public final class ReadRegionTool extends ReadTool {

    public ReadRegionTool() {
        super("read-region");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        BlockPos one = Positions.of(args, "from");
        BlockPos other = Positions.of(args, "to");
        boolean includeAir = new Args(args).bool("includeAir", true);

        /* Either pair of opposite corners describes the same box, so the answer names the low one. */
        BlockPos from = new BlockPos(Math.min(one.getX(), other.getX()), Math.min(one.getY(), other.getY()),
                Math.min(one.getZ(), other.getZ()));
        BlockPos to = new BlockPos(Math.max(one.getX(), other.getX()), Math.max(one.getY(), other.getY()),
                Math.max(one.getZ(), other.getZ()));
        int width = to.getX() - from.getX() + 1;
        int height = to.getY() - from.getY() + 1;
        int depth = to.getZ() - from.getZ() + 1;

        Level level = Mc.requirePlayerEvenIfDead().level();
        Map<Block, Integer> palette = new LinkedHashMap<>();
        Runs runs = new Runs();
        /*
        A box wide enough to overflow an int is refused by mcp-server before it reaches a bot, but
        the e2e harness asks this bot directly and nothing bounds it there. The other kind of bot
        counts these in i64, and two bots that disagree about the size of a box agree about nothing.
        */
        long outside = 0;
        long missing = 0;

        /*
        Written out rather than iterated with BlockPos.betweenClosed or a Cursor3D: those run x
        fastest and z outermost, which is a different order, and a different order is different run
        boundaries and a different palette. The shape below is the contract's, not a preference.
        */
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = from.getY(); y <= to.getY(); y++) {
            if (level.isOutsideBuildHeight(y)) {
                outside += (long) width * depth;
                runs.close();
                continue;
            }
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                for (int x = from.getX(); x <= to.getX(); x++) {
                    at.set(x, y, z);

                    /*
                    Before anything is asked of the state: a chunk the client has not got reads as
                    void_air, so reading first turns an unknown block into air, and with includeAir
                    off into nothing at all -- a hole in the build that the totals still cover for.
                    */
                    if (!level.isLoaded(at)) {
                        missing++;
                        runs.close();
                        continue;
                    }

                    BlockState state = level.getBlockState(at);
                    if (!includeAir && state.isAir()) {
                        runs.close();
                        continue;
                    }
                    runs.add(index(palette, state.getBlock()));
                }
            }
        }
        runs.close();

        JsonArray names = new JsonArray();
        for (Block block : palette.keySet()) {
            /* Vanilla by its bare path, as every other tool words a block; anything else keeps the
               namespace it came from, which is the only thing telling two "stone" blocks apart. */
            names.add(Items.plain(BuiltInRegistries.BLOCK.getKey(block).toString()));
        }

        JsonObject size = new JsonObject();
        size.addProperty("x", width);
        size.addProperty("y", height);
        size.addProperty("z", depth);

        JsonObject data = new JsonObject();
        data.add("from", Positions.json(from));
        data.add("to", Positions.json(to));
        data.add("size", size);
        data.addProperty("blocks", (long) width * height * depth);
        data.add("palette", names);
        data.add("runs", runs.json());
        data.addProperty("missing", missing);
        data.addProperty("outside", outside);
        return data;
    }

    /** Where a block sits in the palette, adding it at the end the first time it is seen. */
    static <T> int index(Map<T, Integer> palette, T block) {
        Integer known = palette.get(block);
        if (known != null) {
            return known;
        }
        int added = palette.size();
        palette.put(block, added);
        return added;
    }

    /** The run being counted, and the ones closed behind it. */
    static final class Runs {
        private final JsonArray closed = new JsonArray();
        private int block;
        private int count;

        void add(int block) {
            if (count > 0 && block == this.block) {
                count++;
                return;
            }
            close();
            this.block = block;
            count = 1;
        }

        void close() {
            if (count == 0) {
                return;
            }
            JsonObject run = new JsonObject();
            run.addProperty("block", block);
            run.addProperty("count", count);
            closed.add(run);
            count = 0;
        }

        JsonArray json() {
            return closed;
        }
    }
}
