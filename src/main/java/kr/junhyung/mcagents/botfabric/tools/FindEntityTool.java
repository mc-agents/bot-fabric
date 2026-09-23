package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.text.Segments;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * What is nearby, nearest first, for deciding whether a mob spawned or an NPC is where it should be.
 *
 * <p>A box may be given instead of a radius. A room is a box: sweeping one with a radius either
 * misses the far corners or drags in the street outside, and a room's furniture is exactly the
 * thing worth listing whole. The answer still comes nearest first, since that is the order that
 * says which of two identical chairs is the one in front of the bot.
 */
public final class FindEntityTool extends ReadTool {

    public FindEntityTool() {
        super("find-entity");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String query = args.get("type").isJsonNull() ? null : args.get("type").getAsString();
        double maxDistance = args.get("maxDistance").getAsDouble();
        int count = args.get("count").getAsInt();
        AABB box = box(args);

        LocalPlayer player = Mc.requirePlayerEvenIfDead();

        List<Entity> loaded = new ArrayList<>();
        List<Entity> found = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity == player) {
                continue;
            }
            loaded.add(entity);
            boolean reached = box == null ? entity.distanceTo(player) <= maxDistance : box.contains(entity.position());
            if (reached && (query == null || Entities.matches(entity, query))) {
                found.add(entity);
            }
        }
        found.sort(Comparator.comparingDouble(player::distanceTo));

        /* From everything loaded: a label out of range can still be over an entity that is in it. */
        Map<Entity, Entity> labels = Nameplates.byEntity(loaded);

        JsonArray entities = new JsonArray();
        for (Entity entity : found.subList(0, Math.min(count, found.size()))) {
            JsonObject described = Entities.describe(entity, player);
            Entity label = labels.get(entity);
            described.addProperty("nameplate", label == null ? null : Nameplates.text(label).getString());
            described.add("nameplateComponent",
                    label == null ? JsonNull.INSTANCE : Segments.raw(Nameplates.text(label)));
            entities.add(described);
        }

        JsonObject data = new JsonObject();
        data.add("query", args.get("type"));
        /* No distance to report is how the server is told a box was searched rather than a radius. */
        data.addProperty("maxDistance", box == null ? maxDistance : 0.0);
        data.add("entities", entities);
        return data;
    }

    /**
     * The box to search, or null for a radius.
     *
     * <p>Both corners are block coordinates and both ends are inclusive, which is how every other
     * tool that takes a box reads one, so the far corner is grown by a block: a box from (0,0,0) to
     * (0,0,0) is the one block at the origin and holds whatever stands in it.
     */
    private static AABB box(JsonObject args) {
        JsonElement from = args.get("from");
        JsonElement to = args.get("to");

        if (from == null || from.isJsonNull() || to == null || to.isJsonNull()) {
            return null;
        }
        BlockPos one = Positions.of(args, "from");
        BlockPos other = Positions.of(args, "to");

        return new AABB(
                Math.min(one.getX(), other.getX()), Math.min(one.getY(), other.getY()), Math.min(one.getZ(), other.getZ()),
                Math.max(one.getX(), other.getX()) + 1.0, Math.max(one.getY(), other.getY()) + 1.0,
                Math.max(one.getZ(), other.getZ()) + 1.0);
    }
}
