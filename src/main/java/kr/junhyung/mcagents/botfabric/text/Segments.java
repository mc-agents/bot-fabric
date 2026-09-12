package kr.junhyung.mcagents.botfabric.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
