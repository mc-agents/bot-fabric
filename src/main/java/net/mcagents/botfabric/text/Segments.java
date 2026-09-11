package net.mcagents.botfabric.text;

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

    public static JsonArray of(Component component) {
        JsonArray segments = new JsonArray();
        if (component == null) {
            return segments;
        }
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                segments.add(segment(style, text));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
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
