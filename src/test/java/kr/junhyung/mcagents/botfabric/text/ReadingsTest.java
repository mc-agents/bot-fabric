package kr.junhyung.mcagents.botfabric.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import kr.junhyung.mcagents.botfabric.text.Readings.Leaf;
import org.junit.jupiter.api.Test;

/**
 * The shown reading has to be what mcp-server renders from the same component, piece for piece,
 * or a pattern copied from read-window fails inside the bot. These vectors are the rules of its
 * Flatten and Piece, and bot-azalea asserts the same ones.
 */
class ReadingsTest {

    private static final String GLYPH = "\uE000";

    @Test
    void piecesWithoutAFontRunTogether() {
        Readings line = Readings.of("Hello world", List.of(new Leaf("Hello ", null), new Leaf("world", null)));

        assertEquals("Hello world", line.shown());
        assertEquals("Hello world", line.readable());
    }

    @Test
    void piecesInAFontAreLabelledAndSeparated() {
        Readings line = Readings.of(GLYPH + "1/2",
                List.of(new Leaf(GLYPH, "mcagents:ui/background"), new Leaf("1/2", "mcagents:ui/page_6")));

        assertEquals("[ui/page_6] 1/2", line.shown());
        assertEquals("1/2", line.readable());
        assertEquals(GLYPH + "1/2", line.raw());
    }

    @Test
    void aPieceMadeOnlyOfGlyphsIsDropped() {
        Readings line = Readings.of(GLYPH + " " + GLYPH + "Mana",
                List.of(new Leaf(GLYPH, "hud:bar"), new Leaf(" " + GLYPH, "hud:bar"), new Leaf("Mana", "hud:label")));

        assertEquals("[label] Mana", line.shown());
    }

    /** Plane 15 and 16 are private use too, and a pack that draws from them is read the same way. */
    @Test
    void aSupplementaryPrivateUseGlyphIsAGlyph() {
        String glyph = new StringBuilder().appendCodePoint(0xF0000).appendCodePoint(0x10FFFD).toString();
        Readings line = Readings.of(glyph + " " + glyph + "Mana",
                List.of(new Leaf(glyph, "hud:bar"), new Leaf(" " + glyph, "hud:bar"), new Leaf("Mana", "hud:label")));

        assertEquals("[label] Mana", line.shown());
        assertEquals(" Mana", line.readable());
    }

    @Test
    void aLeafThatWasOnlyEverASpaceIsKept() {
        Readings line = Readings.of("Cleared 0 [Track]",
                List.of(new Leaf("Cleared 0", null), new Leaf(" ", null), new Leaf("[Track]", null)));

        assertEquals("Cleared 0 [Track]", line.shown());
    }

    /** Between labels the space is a piece of its own, and a blank piece is not written as "[font] ". */
    @Test
    void aSpaceBetweenLabelsIsNotLabelled() {
        Readings line = Readings.of("Level 42",
                List.of(new Leaf("Level", "gui:label"), new Leaf(" ", null), new Leaf("42", null)));

        assertEquals("[label] Level | 42", line.shown());
    }

    @Test
    void nothingReadableFallsBackToTheRawString() {
        Readings line = Readings.of(GLYPH + GLYPH, List.of(new Leaf(GLYPH, "hud:bar"), new Leaf(GLYPH, "hud:bar")));

        assertEquals(GLYPH + GLYPH, line.shown());
        assertEquals("", line.readable());
    }

    @Test
    void theNamespaceIsTakenOffTheFont() {
        Readings line = Readings.of("Probe Chest", List.of(new Leaf("Probe Chest", "mcagents:gui/header")));

        assertEquals("[gui/header] Probe Chest", line.shown());
    }

    @Test
    void colourCodesAreTakenOutAfterTheGlyphs() {
        Readings line = Readings.of("§aMana " + GLYPH + "§r5",
                List.of(new Leaf("§aMana ", "hud:label"), new Leaf(GLYPH + "§r5", null)));

        assertEquals("[label] Mana  | 5", line.shown());
        assertEquals("Mana 5", line.readable());
    }

    /** A pattern matches any reading, and the shown one is what a match is quoted back as. */
    @Test
    void aPatternMatchesTheShownTheReadableOrTheRawText() {
        Readings line = Readings.of(GLYPH + "1/2",
                List.of(new Leaf(GLYPH, "ui:background"), new Leaf("1/2", "ui:page_6")));

        assertEquals("[page_6] 1/2", line.matched(Pattern.compile("^\\[page_6\\] 1/2$")));
        assertEquals("1/2", line.matched(Pattern.compile("^1/2$")));
        assertEquals(GLYPH + "1/2", line.matched(Pattern.compile("^" + GLYPH)));
        assertTrue(line.matches(Pattern.compile("1/2")));
        assertFalse(line.matches(Pattern.compile("2/2")));
        assertNull(line.matched(Pattern.compile("2/2")));
    }
}
