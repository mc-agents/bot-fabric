package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** How an entity is described to a reader, and how a caller's query finds one. */
final class Entities {

    /** Enough to tell a misspelling from an empty world without printing the whole level. */
    private static final int MAX_LISTED = 5;

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
        return id(entity);
    }

    /** The registry id without the namespace. What a thing is, whatever a server named it. */
    static String id(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    /**
     * The catalogue documents the filter as "player", "mob", or part of an entity name, so those two
     * words are the whole category and not a substring of an id. Without them a search for "mob"
     * found nothing on this kind of bot and everything nearby on the other.
     */
    static boolean matches(Entity entity, String query) {
        String needle = Items.plain(query);

        if (needle.equals("player")) {
            return entity instanceof Player;
        }
        if (needle.equals("mob")) {
            return entity instanceof Mob;
        }

        return id(entity).contains(needle) || label(entity).toLowerCase().contains(needle);
    }

    /**
     * How an entity reads in a sentence: the name, and the id after it when a server's name hides
     * what the thing is. An unnamed cow reads "cow" and not "cow (cow)".
     */
    static String named(Entity entity) {
        String label = label(entity);

        return label.equals(id(entity)) ? label : label + " (" + id(entity) + ")";
    }

    /**
     * The nearest entity answering to what a caller typed, or a refusal naming what was in range
     * instead. Being told what is nearby is the difference between "my NPC did not spawn" and "I
     * spelled its name wrong", and that is the whole reason this does not just return null.
     */
    static Entity require(LocalPlayer player, String query, double maxDistance) {
        List<Entity> nearby = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity != player && entity.distanceTo(player) <= maxDistance) {
                nearby.add(entity);
            }
        }
        nearby.sort(Comparator.comparingDouble(player::distanceTo));

        for (Entity entity : nearby) {
            if (matches(entity, query)) {
                return entity;
            }
        }

        String listed = nearby.stream().limit(MAX_LISTED)
                .map(entity -> label(entity) + " (" + Numbers.oneDecimal(entity.distanceTo(player))
                        + " blocks away)")
                .collect(Collectors.joining(", "));

        throw ToolException.refused("NO_SUCH_ENTITY",
                "Nothing within " + Numbers.plain(maxDistance) + " blocks is named like \"" + query + "\". "
                        + (listed.isEmpty() ? "Nothing else is in range either."
                                            : "In range: " + listed + "."));
    }

    static JsonObject describe(Entity entity, Entity from) {
        JsonObject described = new JsonObject();
        described.addProperty("label", label(entity));
        described.addProperty("type", id(entity));
        described.add("position", Positions.json(entity.position()));
        described.addProperty("distance", Math.round(entity.distanceTo(from) * 10.0) / 10.0);
        return described;
    }
}
