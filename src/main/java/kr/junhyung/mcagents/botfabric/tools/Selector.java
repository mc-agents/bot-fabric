package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Which entity interact-entity and attack-entity mean: by name, by the label floating over it, by
 * the id find-entity gave it, or by the crosshair.
 *
 * <p>Exactly one, because two can name different entities and either choice would click something
 * the caller may not have meant. A refusal and not a catalogue error, since the arguments are the
 * shape the catalogue describes and the server would otherwise drop the tool as a mismatch.
 */
final class Selector {

    private static final int MAX_LISTED = 5;

    private final String name;
    private final String label;
    private final Integer id;
    private final boolean crosshair;
    private final double maxDistance;

    private Selector(String name, String label, Integer id, boolean crosshair, double maxDistance) {
        this.name = name;
        this.label = label;
        this.id = id;
        this.crosshair = crosshair;
        this.maxDistance = maxDistance;
    }

    static Selector of(JsonObject args) {
        String name = present(args, "name") ? args.get("name").getAsString() : null;
        String label = present(args, "label") ? args.get("label").getAsString() : null;
        Integer id = present(args, "id") ? args.get("id").getAsInt() : null;
        boolean crosshair = present(args, "crosshair") && args.get("crosshair").getAsBoolean();

        List<String> given = new ArrayList<>();
        if (name != null) {
            given.add("name");
        }
        if (label != null) {
            given.add("label");
        }
        if (id != null) {
            given.add("id");
        }
        if (crosshair) {
            given.add("crosshair");
        }
        if (given.size() != 1) {
            throw ToolException.refused("ONE_SELECTOR",
                    "Say which entity with exactly one of name, label, id or crosshair; this call gave "
                            + gave(given) + ".");
        }
        return new Selector(name, label, id, crosshair, args.get("maxDistance").getAsDouble());
    }

    private static boolean present(JsonObject args, String key) {
        JsonElement value = args.get(key);
        return value != null && !value.isJsonNull();
    }

    private static String gave(List<String> given) {
        if (given.isEmpty()) {
            return "none";
        }
        if (given.size() == 1) {
            return given.getFirst();
        }
        return String.join(", ", given.subList(0, given.size() - 1)) + " and " + given.getLast();
    }

    /** Whether the target is where the crosshair is, so neither walked to nor turned towards. */
    boolean crosshair() {
        return crosshair;
    }

    Entity resolve(LocalPlayer player) {
        if (name != null) {
            return Entities.require(player, name, maxDistance);
        }
        if (id != null) {
            Entity entity = Mc.client().level.getEntity(id);
            if (entity == null || entity == player) {
                throw ToolException.refused("NO_SUCH_ENTITY", "No entity with id " + id + " is in the bot's world.");
            }
            return entity;
        }
        if (label != null) {
            return underLabel(player);
        }
        return inCrosshair().getEntity();
    }

    /**
     * What the crosshair is on, as the client's own hit test found it: the same pick a player's click
     * uses, so an interaction hitbox or an invisible base is reached where a search by name is not.
     */
    static EntityHitResult inCrosshair() {
        HitResult hit = Mc.client().hitResult;
        if (hit instanceof EntityHitResult entity) {
            return entity;
        }
        throw ToolException.refused("NOT_LOOKING_AT_ENTITY", "The crosshair is not on an entity within reach.");
    }

    /** The nearest label that reads like the query and has an entity in range under it. */
    private Entity underLabel(LocalPlayer player) {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity != player) {
                entities.add(entity);
            }
        }

        String needle = label.toLowerCase();
        List<Entity> labels = entities.stream()
                .filter(Nameplates::readable)
                .sorted(Comparator.comparingDouble(player::distanceTo))
                .toList();

        for (Entity candidate : labels) {
            if (!Nameplates.text(candidate).getString().toLowerCase().contains(needle)) {
                continue;
            }
            Entity owner = Nameplates.owner(candidate, entities);
            if (owner != null && owner.distanceTo(player) <= maxDistance) {
                return owner;
            }
        }

        String listed = labels.stream()
                .filter(candidate -> candidate.distanceTo(player) <= maxDistance)
                .limit(MAX_LISTED)
                .map(candidate -> Nameplates.text(candidate).getString() + " ("
                        + Numbers.oneDecimal(candidate.distanceTo(player)) + " blocks away)")
                .collect(Collectors.joining(", "));

        throw ToolException.refused("NO_SUCH_LABEL",
                "Nothing within " + Numbers.plain(maxDistance) + " blocks stands under a label reading like \""
                        + label + "\". " + (listed.isEmpty() ? "No label is in range either."
                                                           : "Labels in range: " + listed + "."));
    }
}
