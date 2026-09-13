package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What a beacon lets a player choose, and which choices the server takes.
 *
 * <p>The screen's effect buttons are icons with no text, and none of them sends anything: pressing
 * one only changes what the confirm button will send, and the confirm button sends both effects in
 * one packet. So there is no button number to press and nothing for press-dialog-button to find.
 *
 * <p>The rules are the screen's and, since 26.2, the server's: an effect from row N needs a pyramid
 * of N levels, regeneration is only ever secondary, and a secondary needs all four levels and is
 * either regeneration or the primary again, which is how the primary becomes level II. 26.2 drops
 * the connection of a client that sends anything else, or sends with nothing in the payment slot.
 * 26.1.2 is quieter and worse: it ignores a send without payment, and it applies an effect from a
 * row the pyramid has not reached, which is a beacon no player could have set.
 */
final class Beacons {

    /** The payment slot is the menu's first; the player's inventory follows it. */
    static final int PAYMENT_SLOT = 0;

    /** The rows of {@link BeaconBlockEntity#BEACON_EFFECTS}; the last one is the secondary row. */
    private static final int SECONDARY_LEVELS = BeaconBlockEntity.BEACON_EFFECTS.size();

    record Choice(Holder<MobEffect> effect, boolean primary, int levels, boolean upgrade) {

        String name() {
            return ContainerOptions.id(effect);
        }

        /** The words the screen's tooltip uses, with the " II" it adds on the upgrade button. */
        String label() {
            String label = Component.translatable(effect.value().getDescriptionId()).getString();
            return upgrade ? label + " II" : label;
        }
    }

    private Beacons() {
    }

    /**
     * The buttons in the order the screen lays them out: the three primary rows, then regeneration,
     * then the upgrade button, which is the primary again and is only there once a primary is set.
     */
    static List<Choice> choices(BeaconMenu menu) {
        List<Choice> choices = new ArrayList<>();
        List<List<Holder<MobEffect>>> rows = BeaconBlockEntity.BEACON_EFFECTS;

        for (int row = 0; row < rows.size(); row++) {
            for (Holder<MobEffect> effect : rows.get(row)) {
                choices.add(new Choice(effect, row < SECONDARY_LEVELS - 1, row + 1, false));
            }
        }

        Holder<MobEffect> primary = menu.getPrimaryEffect();
        if (primary != null) {
            choices.add(new Choice(primary, false, SECONDARY_LEVELS, true));
        }
        return choices;
    }

    static JsonObject describe(BeaconMenu menu) {
        int levels = menu.getLevels();
        ItemStack payment = menu.getSlot(PAYMENT_SLOT).getItem();

        JsonArray effects = new JsonArray();
        for (Choice choice : choices(menu)) {
            /* The upgrade button is the primary, so it is selected when the secondary is that same effect. */
            Holder<MobEffect> current = choice.primary() ? menu.getPrimaryEffect() : menu.getSecondaryEffect();
            boolean selected = choice.effect().equals(current);

            JsonObject entry = new JsonObject();
            entry.addProperty("name", choice.name());
            entry.addProperty("label", choice.label());
            entry.addProperty("slot", choice.primary() ? "primary" : "secondary");
            entry.addProperty("levels", choice.levels());
            entry.addProperty("available", levels >= choice.levels());
            entry.addProperty("selected", selected);
            effects.add(entry);
        }

        JsonObject beacon = new JsonObject();
        beacon.addProperty("levels", levels);
        beacon.addProperty("payment", payment.isEmpty() ? null : Items.name(payment));
        beacon.add("effects", effects);
        return beacon;
    }

    /**
     * An effect a beacon can give, by id or by the name the screen shows. Anything else is refused
     * here, because the server would store it as no effect at all and say nothing.
     */
    static Holder<MobEffect> effect(String wanted) {
        String plain = Items.plain(wanted);

        for (List<Holder<MobEffect>> row : BeaconBlockEntity.BEACON_EFFECTS) {
            for (Holder<MobEffect> effect : row) {
                String label = Component.translatable(effect.value().getDescriptionId()).getString();
                if (plain.equals(ContainerOptions.id(effect)) || wanted.strip().equalsIgnoreCase(label)) {
                    return effect;
                }
            }
        }
        throw ToolException.refused("NO_SUCH_EFFECT", "a beacon gives none called \"" + wanted + "\"; it gives "
                + BeaconBlockEntity.BEACON_EFFECTS.stream().flatMap(List::stream)
                        .map(ContainerOptions::id).collect(Collectors.joining(", ")));
    }

    /** Refuses, with the reason, the pair the confirm button could not have sent. */
    static void check(BeaconMenu menu, Holder<MobEffect> primary, Holder<MobEffect> secondary) {
        int levels = menu.getLevels();
        int primaryLevels = levelsFor(primary);

        if (primaryLevels >= SECONDARY_LEVELS) {
            throw refused(named(primary) + " is only ever a secondary effect; the primary is one of "
                    + primaries());
        }
        if (primaryLevels > levels) {
            throw refused(named(primary) + " needs a pyramid of " + levels(primaryLevels) + ", and this beacon's "
                    + pyramid(levels));
        }
        if (secondary != null && levels < SECONDARY_LEVELS) {
            throw refused("a secondary effect needs a pyramid of " + levels(SECONDARY_LEVELS)
                    + ", and this beacon's " + pyramid(levels) + "; leave secondary out");
        }
        if (secondary != null && levelsFor(secondary) < SECONDARY_LEVELS && !secondary.equals(primary)) {
            throw refused("the secondary effect is regeneration or the primary again, which makes "
                    + named(primary) + " level II; " + named(secondary) + " is neither");
        }
        if (menu.getSlot(PAYMENT_SLOT).getItem().isEmpty()) {
            throw ToolException.refused("NO_PAYMENT", "the beacon takes one iron ingot, gold ingot, emerald, "
                    + "diamond or netherite ingot for every change, and its payment slot (slot "
                    + PAYMENT_SLOT + ") is empty; put one there with click-slot first");
        }
    }

    static String named(Holder<MobEffect> effect) {
        return Component.translatable(effect.value().getDescriptionId()).getString()
                + " [" + ContainerOptions.id(effect) + "]";
    }

    private static int levelsFor(Holder<MobEffect> effect) {
        List<List<Holder<MobEffect>>> rows = BeaconBlockEntity.BEACON_EFFECTS;
        for (int row = 0; row < rows.size(); row++) {
            if (rows.get(row).contains(effect)) {
                return row + 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static String primaries() {
        return BeaconBlockEntity.BEACON_EFFECTS.subList(0, SECONDARY_LEVELS - 1).stream()
                .flatMap(List::stream).map(ContainerOptions::id).collect(Collectors.joining(", "));
    }

    private static String pyramid(int levels) {
        return levels == 0 ? "has none under it" : "has " + levels(levels);
    }

    private static String levels(int levels) {
        return levels + (levels == 1 ? " level" : " levels");
    }

    private static ToolException refused(String message) {
        return ToolException.refused("EFFECT_UNAVAILABLE", message);
    }
}
