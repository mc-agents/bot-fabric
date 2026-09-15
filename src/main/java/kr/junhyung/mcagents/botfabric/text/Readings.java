package kr.junhyung.mcagents.botfabric.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The three ways a line reads, for a pattern written against it.
 *
 * <p>A caller writes a pattern against what the tools show, and what they show is mcp-server's
 * rendering: the glyphs gone, a font label in front of every piece drawn in one, " | " between
 * them. The bot matches inside itself, before anything has gone to mcp-server, so it renders the
 * same way here -- the rules are {@code Flatten} and {@code Piece} over there, mirrored piece for
 * piece -- and a pattern that matches {@code "[gui/page] 1/2"} matches the window whose title
 * read-window shows as that.
 *
 * <p>The plain text stays a candidate beside it, twice over: as the component's own string, so a
 * pattern written with a glyph in it goes on matching, and with the glyphs and the colour codes
 * taken out, which is what a pattern written against the sentence matched before there were labels.
 */
public record Readings(String raw, String readable, String shown) {

    /** One leaf as the game draws it: its literal text, and the font it inherits or null for the default. */
    public record Leaf(String text, String font) {
    }

    private static final Pattern COLOUR_CODES = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    /**
     * Visited rather than serialised: a translate key is resolved into the words the game draws,
     * which is the one thing mcp-server leaves to the bot.
     */
    public static Readings of(Component component) {
        List<Leaf> leaves = new ArrayList<>();
        component.visit((style, text) -> {
            leaves.add(new Leaf(text, Segments.fontOf(style)));
            return Optional.empty();
        }, Style.EMPTY);
        return of(component.getString(), leaves);
    }

    /** An id, which reads one way only. */
    public static Readings plain(String text) {
        return new Readings(text, text, text);
    }

    static Readings of(String raw, List<Leaf> leaves) {
        return new Readings(raw, readable(raw), shown(raw, leaves));
    }

    /** The text with the glyphs taken out and then the colour codes, in that order. */
    public static String readable(String raw) {
        return COLOUR_CODES.matcher(Segments.GLYPHS.matcher(raw).replaceAll("")).replaceAll("");
    }

    public boolean matches(Pattern pattern) {
        return matched(pattern) != null;
    }

    /**
     * The reading the pattern matched, or null. The shown one comes first because it is what the
     * caller sees in the tools, and so what a matched line should be quoted back as.
     */
    public String matched(Pattern pattern) {
        for (String candidate : List.of(shown, readable, raw)) {
            if (pattern.matcher(candidate).find()) {
                return candidate;
            }
        }
        return null;
    }

    private static String shown(String raw, List<Leaf> leaves) {
        List<Leaf> pieces = new ArrayList<>();
        for (Leaf leaf : leaves) {
            String readable = readable(leaf.text());
            boolean spacer = readable.isBlank() && readable.length() != leaf.text().length();
            if (!readable.isEmpty() && !spacer) {
                pieces.add(new Leaf(readable, leaf.font()));
            }
        }
        if (pieces.isEmpty()) {
            return raw;
        }
        if (pieces.stream().noneMatch(piece -> piece.font() != null)) {
            return pieces.stream().map(Leaf::text).collect(Collectors.joining());
        }
        return pieces.stream()
                .filter(piece -> !piece.text().isBlank())
                .map(Readings::label)
                .collect(Collectors.joining(" | "));
    }

    private static String label(Leaf piece) {
        if (piece.font() == null) {
            return piece.text();
        }
        return "[" + piece.font().substring(piece.font().indexOf(':') + 1) + "] " + piece.text();
    }
}
