package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A corner of the wrong shape is the caller's mistake and has to read as one. Gson answers an
 * absent axis with null and one it cannot read by throwing, and either, left to it, reaches the
 * caller as INTERNAL -- the fault class that says the bot is broken and there is nothing to fix at
 * their end. The message names the corner as well as the axis, because a region has two of them.
 *
 * <p>What the guard must not do is narrow what a caller may send. Eleven tools take a bare
 * position and every one of them handed gson the value directly before the guard was written, so
 * whatever gson read for them then still has to read now.
 */
class PositionsTest {

    private static JsonObject args(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static String refusal(Executable refused) {
        ToolException thrown = assertThrows(ToolException.class, refused);
        assertEquals("BAD_ARGS", thrown.code());
        return thrown.getMessage();
    }

    @Test
    void aCornerMissingAnAxisIsRefusedByTheAxisItIsMissing() {
        JsonObject args = args("""
                {"from": {"x": 1, "y": 2}}
                """);

        assertEquals("expected a number for from.z", refusal(() -> Positions.of(args, "from")));
    }

    @Test
    void anAxisThatIsNotANumberIsRefusedRatherThanThrownFrom() {
        JsonObject args = args("""
                {"to": {"x": "north", "y": 2, "z": 3}}
                """);

        assertEquals("expected a number for to.x", refusal(() -> Positions.of(args, "to")));
    }

    @Test
    void aCornerThatIsNotAnObjectIsRefusedAsAWhole() {
        JsonObject args = args("""
                {"from": 3}
                """);

        assertEquals("expected from as an object with x, y and z", refusal(() -> Positions.of(args, "from")));
    }

    /** The bare form the other eleven tools call. It has no corner to name, so it names none. */
    @Test
    void aBarePositionMissingAnAxisIsRefusedByTheAxisAlone() {
        JsonObject args = args("""
                {"x": 1, "y": 2}
                """);

        assertEquals("expected a number for z", refusal(() -> Positions.of(args)));
    }

    /**
     * A coordinate quoted as a string, which gson has always read as the number it spells. Every
     * tool that takes a position took {@code {"x": "5"}} before the guard was in front of it, and
     * a guard that starts refusing it changes eleven tools without anybody asking for it.
     */
    @Test
    void aCoordinateQuotedAsAStringIsStillRead() {
        assertEquals(new BlockPos(5, -2, 7), Positions.of(args("""
                {"x": "5", "y": "-2", "z": 7}
                """)));
        assertEquals(new BlockPos(5, -2, 7), Positions.of(args("""
                {"from": {"x": "5", "y": "-2", "z": 7}}
                """), "from"));
    }
}
