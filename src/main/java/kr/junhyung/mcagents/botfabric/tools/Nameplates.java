package kr.junhyung.mcagents.botfabric.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The text floating over an NPC, and which entity it floats over.
 *
 * <p>An NPC on a server that draws with a resource pack has no name of its own. It is a mannequin,
 * or a model clicked through an invisible base or an interaction hitbox, and the name a player
 * reads is a separate text display above it. Nothing in the game links the two, so the link is a
 * rule about where they stand, and the catalogue states the same rule for every kind of bot.
 */
final class Nameplates {

    /** How far below a label an entity's feet may be. A tall model's label sits well above its base. */
    static final double BELOW = 3.0;

    /** How far to the side, so a label over one NPC is not read as belonging to its neighbour. */
    static final double ASIDE = 1.0;

    private Nameplates() {
    }

    /**
     * Whether feet at this offset from a label stand under it. The offset is the label's position
     * minus the feet, so a label above the feet has a positive y.
     */
    static boolean under(double dx, double dy, double dz) {
        return dy >= 0 && dy <= BELOW && dx * dx + dz * dz <= ASIDE * ASIDE;
    }

    /**
     * What a label says, or null for an entity that is not one. An armor stand counts only when it
     * is invisible and shows its name, which is a hologram; a visible one is something to click.
     */
    static Component text(Entity entity) {
        if (entity instanceof Display.TextDisplay display) {
            return display.textRenderState() == null ? null : display.textRenderState().text();
        }
        if (entity instanceof ArmorStand stand && stand.isInvisible() && stand.isCustomNameVisible()
                && stand.hasCustomName()) {
            return stand.getCustomName();
        }
        return null;
    }

    static boolean readable(Entity entity) {
        Component text = text(entity);
        return text != null && !text.getString().isBlank();
    }

    /**
     * The entity a label belongs to: of everything a player could click standing under it, the one
     * nearest the label. Displays and other labels are not clickable, whatever their bounds say.
     */
    static Entity owner(Entity label, List<Entity> entities) {
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (entity == label || entity instanceof Display || text(entity) != null || !entity.isPickable()) {
                continue;
            }
            if (!under(label.getX() - entity.getX(), label.getY() - entity.getY(), label.getZ() - entity.getZ())) {
                continue;
            }
            double distance = entity.position().distanceToSqr(label.position());
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * The label over each entity that has one. A label belongs to one entity; an entity under two,
     * a name and a title stacked as separate displays, is labelled by the lower of them.
     */
    static Map<Entity, Entity> byEntity(List<Entity> entities) {
        Map<Entity, Entity> labels = new HashMap<>();

        for (Entity label : entities) {
            if (!readable(label)) {
                continue;
            }
            Entity owner = owner(label, entities);
            if (owner == null) {
                continue;
            }
            labels.merge(owner, label, (kept, other) ->
                    other.position().distanceToSqr(owner.position()) < kept.position().distanceToSqr(owner.position())
                            ? other : kept);
        }
        return labels;
    }
}
