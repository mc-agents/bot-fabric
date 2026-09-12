package kr.junhyung.mcagents.botfabric.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.junhyung.mcagents.botfabric.Mc;
import kr.junhyung.mcagents.botfabric.tool.ReadTool;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import kr.junhyung.mcagents.botfabric.text.Segments;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

/**
 * Text standing in the world: holograms, name tags, floating labels.
 *
 * <p>A server writes half its instructions this way, and none of it is chat, so a bot that only
 * reads chat is missing the half a person would have read first.
 */
public final class ReadDisplaysTool extends ReadTool {

    public ReadDisplaysTool() {
        super("read-displays");
    }

    /**
     * What the thing says, as the component the client was given.
     *
     * <p>A text display keeps its text in render state and carries no custom name, so falling back
     * to the name gave "text_display" where a hologram plainly read something else. Render state
     * arrives with the entity's data and can be a tick behind it.
     */
    private static Component component(Entity entity) {
        if (entity instanceof Display.TextDisplay text && text.textRenderState() != null) {
            return text.textRenderState().text();
        }
        if (entity.hasCustomName()) {
            return entity.getCustomName();
        }
        return Component.literal(Entities.label(entity));
    }

    @Override
    protected JsonObject read(JsonObject args) {
        double maxDistance = args.get("maxDistance").getAsDouble();
        int count = args.get("count").getAsInt();

        LocalPlayer player = Mc.requirePlayer();

        List<Entity> showing = new ArrayList<>();
        for (Entity entity : Mc.client().level.entitiesForRendering()) {
            if (entity == player || entity.distanceTo(player) > maxDistance) {
                continue;
            }
            if (entity.hasCustomName() || entity instanceof Display.TextDisplay) {
                showing.add(entity);
            }
        }
        showing.sort(Comparator.comparingDouble(player::distanceTo));

        JsonArray displays = new JsonArray();
        for (Entity entity : showing) {
            if (displays.size() >= count) {
                break;
            }

            Component said = component(entity);
            int glyphs = Segments.glyphPieces(said);
            String readable = Segments.describe(said);

            /*
            A display with neither readable text nor a glyph in it is not on screen at all, and the
            other kind of bot leaves those out for the same reason. One made only of glyphs is an
            icon and is kept: something is there. Counting after the filter rather than before is
            what makes count mean "this many displays" instead of "this many entities looked at".
            */
            if (readable.isEmpty() && glyphs == 0) {
                continue;
            }

            JsonObject display = new JsonObject();
            display.addProperty("text", readable);
            display.add("segments", Segments.of(said));
            display.addProperty("glyphPieces", glyphs);
            display.add("component", Segments.raw(said));
            display.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
            display.add("position", Positions.json(entity.position()));
            display.addProperty("distance", Math.round(entity.distanceTo(player) * 10.0) / 10.0);
            displays.add(display);
        }

        JsonObject data = new JsonObject();
        data.addProperty("maxDistance", maxDistance);
        data.add("displays", displays);
        return data;
    }
}
