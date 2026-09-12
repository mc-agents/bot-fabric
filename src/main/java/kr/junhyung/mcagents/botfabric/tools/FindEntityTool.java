package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** What is nearby, nearest first, for deciding whether a mob spawned or an NPC is where it should be. */
public final class FindEntityTool extends ReadTool {

    public FindEntityTool() {
        super("find-entity");
    }

    @Override
    protected JsonObject read(JsonObject args) {
        String query = args.get("type").isJsonNull() ? null : args.get("type").getAsString();
        double maxDistance = args.get("maxDistance").getAsDouble();
        int count = args.get("count").getAsInt();

        LocalPlayer player = Mc.requirePlayer();

        List<Entity> found = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity == player || entity.distanceTo(player) > maxDistance) {
                continue;
            }
            if (query == null || Entities.matches(entity, query)) {
                found.add(entity);
            }
        }
        found.sort(Comparator.comparingDouble(player::distanceTo));

        JsonArray entities = new JsonArray();
        for (Entity entity : found.subList(0, Math.min(count, found.size()))) {
            entities.add(Entities.describe(entity, player));
        }

        JsonObject data = new JsonObject();
        data.add("query", args.get("type"));
        data.addProperty("maxDistance", maxDistance);
        data.add("entities", entities);
        return data;
    }
}
