package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.BlockPos;

/** Coordinates, in and out. The server sends them already floored. */
final class Positions {

    private Positions() {
    }

    static BlockPos of(JsonObject args) {
        return at(args, "");
    }

    /** A corner carried as an object of its own, the way a region names its two ends. */
    static BlockPos of(JsonObject args, String key) {
        JsonElement corner = args.get(key);
        if (corner == null || !corner.isJsonObject()) {
            throw ToolException.badArgs("expected " + key + " as an object with x, y and z");
        }
        /* Named so that a region, which has two of these, says which corner is the bad one. */
        return at(corner.getAsJsonObject(), key + ".");
    }

    private static BlockPos at(JsonObject position, String named) {
        return new BlockPos(coordinate(position, named, "x"), coordinate(position, named, "y"),
                coordinate(position, named, "z"));
    }

    /**
     * One axis. What is caught here is the way gson refuses -- an absent key answered with null, a
     * value it cannot read answered by throwing -- because either, left alone, reaches the caller
     * as INTERNAL, the fault class that says the bot is broken, when what is broken is the
     * arguments it was handed.
     *
     * <p>Gson is still the one doing the reading, and that is deliberate: it takes {@code "5"} as
     * 5, so a quoted coordinate has worked in every tool that takes a position for as long as
     * there have been any. Refusing one here would be a break wearing a fix's clothes.
     */
    private static int coordinate(JsonObject position, String named, String axis) {
        JsonElement value = position.get(axis);
        if (value == null || value.isJsonNull()) {
            throw ToolException.badArgs("expected a number for " + named + axis);
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException unreadable) {
            throw ToolException.badArgs("expected a number for " + named + axis);
        }
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
