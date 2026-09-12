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

    static JsonObject json(BlockPos at) {
        JsonObject position = new JsonObject();
        position.addProperty("x", at.getX());
        position.addProperty("y", at.getY());
        position.addProperty("z", at.getZ());
        return position;
    }

    static JsonObject json(net.minecraft.world.phys.Vec3 at) {
        JsonObject position = new JsonObject();
        position.addProperty("x", (int) Math.floor(at.x));
        position.addProperty("y", (int) Math.floor(at.y));
        position.addProperty("z", (int) Math.floor(at.z));
        return position;
    }
}
