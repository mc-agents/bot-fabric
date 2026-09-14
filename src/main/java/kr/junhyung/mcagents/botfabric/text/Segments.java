package kr.junhyung.mcagents.botfabric.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import kr.junhyung.mcagents.botfabric.Mc;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

public final class Segments {
    private Segments() {
    }

    /**
     * The pieces a component is drawn from, with the glyphs taken out.
     *
     * <p>A server draws a HUD by stacking a bar glyph, a spacer, and a label. The glyphs are
     * private use area codepoints that mean nothing as text, and a spacer with them removed holds
     * whitespace and nothing else: it is a position on the screen rather than something to read,
     * so it does not become a segment.
     *
     * <p>What is left is not trimmed. "Mana " and "Mana" are different pieces, and a server that
     * writes a label and a number as two components puts the space in one of them.
     */
    /**
     * How many pieces held nothing but glyphs.
     *
     * <p>On a HUD those are spacers and dropping them is right. On a display entity a piece made
     * only of glyphs is an icon: it says something is there and there is nothing to read, which is
     * a different thing from an empty display, and a real server's nameplates came back as blank
     * lines until the count went with them.
     */
    public static int glyphPieces(Component component) {
        if (component == null) {
            return 0;
        }
        int[] count = new int[1];
        component.visit((style, text) -> {
            if (!text.isBlank() && GLYPHS.matcher(text).replaceAll("").isBlank()) {
                count[0]++;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return count[0];
    }

    public static JsonArray of(Component component) {
        JsonArray segments = new JsonArray();
        if (component == null) {
            return segments;
        }
        component.visit((style, text) -> {
            String readable = GLYPHS.matcher(text).replaceAll("");
            if (!readable.isBlank()) {
                segments.add(segment(style, readable));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    /* The private use area, where a resource pack puts the glyphs it draws a HUD out of. */
    private static final java.util.regex.Pattern GLYPHS =
            java.util.regex.Pattern.compile("[\\uE000-\\uF8FF]|[\\uDB80-\\uDBBF][\\uDC00-\\uDFFF]");

    /**
     * The readable pieces joined, for the {@code text} field a DTO keeps beside its segments. A
     * caller reading the wire by hand wants one string; mcp-server writes the real sentence from
     * the pieces.
     */
    public static String describe(Component component) {
        StringBuilder said = new StringBuilder();
        for (var element : of(component)) {
            if (!said.isEmpty()) {
                said.append(' ');
            }
            said.append(element.getAsJsonObject().get("text").getAsString());
        }
        return said.toString();
    }

    /**
     * The text as it reads with the glyphs taken out and nothing between the pieces: what a pattern
     * written against a feed line is matched with inside the bot, where the font labels mcp-server
     * puts in front of each piece do not exist.
     */
    public static String readable(Component component) {
        return GLYPHS.matcher(component.getString()).replaceAll("");
    }

    /**
     * The component as Minecraft's own JSON, so mcp-server can do the flattening itself.
     *
     * <p>The game serialises it: no library sits between the client and the wire, which is what
     * keeps following a new Minecraft version down to two files. Null when there is no connection
     * to borrow a registry from -- the codec needs one for the item a hover event can carry.
     */
    public static JsonElement raw(Component component) {
        if (component == null) {
            return JsonNull.INSTANCE;
        }

        ClientPacketListener connection = Mc.client().getConnection();
        if (connection == null) {
            return JsonNull.INSTANCE;
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess());

        return ComponentSerialization.CODEC.encodeStart(ops, component).result().orElse(JsonNull.INSTANCE);
    }

    private static JsonObject segment(Style style, String text) {
        JsonObject segment = new JsonObject();
        segment.addProperty("text", text);
        String font = fontOf(style);
        if (font != null) {
            segment.addProperty("font", font);
        }
        TextColor color = style.getColor();
        if (color != null) {
            segment.addProperty("color", color.serialize());
        }
        return segment;
    }

    private static String fontOf(Style style) {
        FontDescription font = style.getFont();
        if (font instanceof FontDescription.Resource resource && !resource.equals(FontDescription.DEFAULT)) {
            return resource.id().toString();
        }
        return null;
    }
}
