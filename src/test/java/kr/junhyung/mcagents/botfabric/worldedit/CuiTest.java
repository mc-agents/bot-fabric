package kr.junhyung.mcagents.botfabric.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The messages asserted here are WorldEdit's own, in the form
 * {@code com.sk89q.worldedit.internal.cui.SelectionPointEvent} and the Bukkit player that sends it
 * produce: the type id, then the parameters, joined with pipes. What a bot does with them is the
 * part that can be wrong, and the case that matters is a shape arriving between two selections.
 */
class CuiTest {

    @BeforeEach
    void clear() {
        Cui.forget();
    }

    @Test
    void aCornerIsReadWhereTheServerPutIt() {
        Cui.accept("s|cuboid");
        Cui.accept("p|0|12|64|-30|1");
        Cui.accept("p|1|14|66|-28|27");

        assertEquals("cuboid", Cui.shape());
        assertEquals(27, Cui.volume());
        assertEquals(List.of(0, 1), Cui.corners().stream().map(Cui.Corner::index).toList());
        assertEquals(-30, Cui.corners().getFirst().at().getZ());
        assertEquals(66, Cui.corners().getLast().at().getY());
    }

    /**
     * A shape starts a description over. Without that, a selection that was cleared and described
     * again would still answer with the corners of the one that is gone, which is the difference
     * between "nothing is selected" and putting a box down in the wrong place.
     */
    @Test
    void aShapeDropsTheCornersBeforeIt() {
        Cui.accept("s|cuboid");
        Cui.accept("p|0|12|64|-30|1");
        Cui.accept("p|1|14|66|-28|27");
        Cui.accept("s|cuboid");

        assertTrue(Cui.corners().isEmpty());
        assertEquals(-1, Cui.volume());
    }

    /** {@code //pos1} on its own moves one corner and WorldEdit describes only that one. */
    @Test
    void oneCornerChangesWithoutTheOther() {
        Cui.accept("s|cuboid");
        Cui.accept("p|0|12|64|-30|1");
        Cui.accept("p|1|14|66|-28|27");

        int described = Cui.described();

        Cui.accept("p|0|0|64|0|29767");

        assertEquals(described + 1, Cui.described());
        assertEquals(2, Cui.corners().size());
        assertEquals(0, Cui.corners().getFirst().at().getX());
        assertEquals(14, Cui.corners().getLast().at().getX());
    }

    /**
     * The channel carries more than this reads -- colours, ellipsoids, the multi-region events --
     * and a message it cannot make sense of must leave what it already knows alone rather than
     * half-applying itself.
     */
    @Test
    void whatCannotBeReadChangesNothing() {
        Cui.accept("s|cuboid");
        Cui.accept("p|0|12|64|-30|1");

        int described = Cui.described();

        Cui.accept("col|0xff0000ff|0x00ff00ff|0x0000ffff|0xffffffff");
        Cui.accept("p|0|not-a-number|64|-30|1");
        Cui.accept("p|1|3|4");
        Cui.accept("");

        assertEquals(described, Cui.described());
        assertEquals(1, Cui.corners().size());
        assertEquals(12, Cui.corners().getFirst().at().getX());
    }

    @Test
    void anotherServerStartsWithNothing() {
        Cui.accept("s|cuboid");
        Cui.accept("p|0|12|64|-30|1");
        Cui.forget();

        assertNull(Cui.shape());
        assertTrue(Cui.corners().isEmpty());
        assertEquals(0, Cui.described());
    }
}
