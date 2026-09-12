package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.junhyung.mcagents.botfabric.tool.ToolException;
import org.junit.jupiter.api.Test;

/**
 * The catalogue's {@code titlePattern} is documented as a JavaScript regular expression, and the
 * two kinds of bot compile it with two different engines. What is asserted here is that they agree
 * about the two things a caller relies on: an unanchored pattern matches part of a title, and an
 * anchored one does not match a longer one. bot-mineflayer asserts the same two.
 */
class WaitForWindowToolTest {

    @Test
    void anUnanchoredPatternMatchesPartOfTheTitle() {
        assertTrue(WaitForWindowTool.titleMatches("Shop: weapons", WaitForWindowTool.compile("Shop")));
        assertFalse(WaitForWindowTool.titleMatches("Shop: weapons", WaitForWindowTool.compile("Bank")));
    }

    @Test
    void anAnchoredPatternOnlyMatchesTheWholeTitle() {
        assertTrue(WaitForWindowTool.titleMatches("Shop", WaitForWindowTool.compile("^Shop$")));
        assertFalse(WaitForWindowTool.titleMatches("Shop: weapons", WaitForWindowTool.compile("^Shop$")));
    }

    /** No pattern means any window, which is the catalogue's default and not "a window named null". */
    @Test
    void noPatternMatchesAnything() {
        assertTrue(WaitForWindowTool.titleMatches("anything at all", WaitForWindowTool.compile(null)));
    }

    /**
     * A pattern Java cannot compile has to be said rather than left to never match: a caller who
     * wrote it wrong would otherwise be told that no window opened, and would look at the server.
     */
    @Test
    void aBrokenPatternIsRefusedWithTheSourceQuotedBack() {
        ToolException refused = assertThrows(ToolException.class, () -> WaitForWindowTool.compile("Shop(["));

        assertEquals("BAD_ARGS", refused.code());
        assertTrue(refused.getMessage().startsWith("\"Shop([\" is not a valid regular expression: "),
                refused.getMessage());
    }
}
