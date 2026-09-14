package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
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

        LocalPlayer player = Mc.requirePlayerEvenIfDead();

        List<Entity> loaded = new ArrayList<>();
        List<Entity> found = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity == player) {
                continue;
            }
            loaded.add(entity);
            if (entity.distanceTo(player) <= maxDistance && (query == null || Entities.matches(entity, query))) {
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
        data.addProperty("maxDistance", maxDistance);
        data.add("entities", entities);
        return data;
    }
}
