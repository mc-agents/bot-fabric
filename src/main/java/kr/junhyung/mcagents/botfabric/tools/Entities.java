package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** How an entity is described to a reader, and how a caller's query finds one. */
final class Entities {

    private Entities() {
    }

    /**
     * The name a server gave it, else its type. What a caller reads on screen is the custom name,
     * so looking for "Probe Cow" has to work as well as looking for "cow".
     */
    static String label(Entity entity) {
        if (entity.hasCustomName()) {
            return entity.getCustomName().getString();
        }
        if (entity instanceof Player player) {
            return player.getGameProfile().name();
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    static boolean matches(Entity entity, String query) {
        String needle = Items.plain(query);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

        return type.contains(needle) || label(entity).toLowerCase().contains(needle);
    }

    /** Broad enough to act on: whether it can be attacked, talked to, or only looked at. */
    static String kind(Entity entity) {
        if (entity instanceof Player) {
            return "player";
        }
        if (entity instanceof Mob mob) {
            return mob.getType().getCategory().isFriendly() ? "animal" : "hostile";
        }
        if (entity instanceof LivingEntity) {
            return "animal";
        }
        return "other";
    }

    static JsonObject describe(Entity entity, Entity from) {
        JsonObject described = new JsonObject();
        described.addProperty("label", label(entity));
        described.addProperty("type", kind(entity));
        described.add("position", Positions.json(entity.position()));
        described.addProperty("distance", Math.round(entity.distanceTo(from) * 10.0) / 10.0);
        return described;
    }
}
