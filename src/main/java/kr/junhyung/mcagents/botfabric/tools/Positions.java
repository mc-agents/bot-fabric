package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/** Coordinates, in and out. The server sends them already floored. */
final class Positions {

    private Positions() {
    }

    static BlockPos of(JsonObject args) {
        return new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
    }

    /** How a coordinate reads in a sentence, in the shape bot-mineflayer prints it. */
    static String point(BlockPos at) {
        return "(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
    }

    static JsonObject json(BlockPos at) {
        JsonObject position = new JsonObject();
        position.addProperty("x", at.getX());
        position.addProperty("y", at.getY());
        position.addProperty("z", at.getZ());
        return position;
    }

    /** The nearest block of a kind within a radius, or null. Used to find a crafting table. */
    static BlockPos nearest(net.minecraft.client.player.LocalPlayer player,
            net.minecraft.world.level.block.Block block, int radius) {
        BlockPos feet = player.blockPosition();
        BlockPos nearest = null;

        for (BlockPos at : BlockPos.betweenClosed(feet.offset(-radius, -radius, -radius),
                feet.offset(radius, radius, radius))) {
            if (!player.level().getBlockState(at).is(block)) {
                continue;
            }
            if (nearest == null || at.distSqr(feet) < nearest.distSqr(feet)) {
                nearest = at.immutable();
            }
        }
        return nearest;
    }

    static JsonObject json(net.minecraft.world.phys.Vec3 at) {
        JsonObject position = new JsonObject();
        position.addProperty("x", (int) Math.floor(at.x));
        position.addProperty("y", (int) Math.floor(at.y));
        position.addProperty("z", (int) Math.floor(at.z));
        return position;
    }
}
