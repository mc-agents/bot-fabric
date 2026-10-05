package kr.junhyung.mcagents.botfabric.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The two distances have different floors, and for a year one number was set into both.
 *
 * <p>Render distance accepts 2..32 and simulation distance 5..32, and {@code OptionInstance.set}
 * does not clamp a value outside a range: it validates, fails, and keeps the option's own initial
 * value. For simulation distance that value is twelve. So a bot asked to draw two chunks simulated
 * twelve -- more than the eight it would have had if nobody had touched it, in the one setting that
 * exists to bring a container's memory down.
 *
 * <p>Nothing could have caught it. {@code apply} needs a live {@code Options}, so the arithmetic is
 * pulled out to where a test can reach it, and the numbers below are the client's own range rather
 * than this class's opinion of it.
 */
class RenderOptionsTest {

    @Test
    void aDistanceTheSimulationCannotFollowIsRaisedToWhatItTakes() {
        assertEquals(5, RenderOptions.simulationFrom(2));
        assertEquals(5, RenderOptions.simulationFrom(3));
        assertEquals(5, RenderOptions.simulationFrom(4));
    }

    @Test
    void aDistanceTheSimulationTakesIsPassedThrough() {
        assertEquals(5, RenderOptions.simulationFrom(5));
        assertEquals(8, RenderOptions.simulationFrom(8));
        assertEquals(32, RenderOptions.simulationFrom(32));
    }

    @Test
    void whatIsReadFromTheEnvironmentStaysInsideTheRangeTheClientAccepts() {
        assertEquals(8, RenderOptions.distanceFrom(null));
        assertEquals(8, RenderOptions.distanceFrom(""));
        assertEquals(8, RenderOptions.distanceFrom("not a number"));
        assertEquals(2, RenderOptions.distanceFrom("2"));
        assertEquals(2, RenderOptions.distanceFrom("1"));
        assertEquals(32, RenderOptions.distanceFrom("64"));
        assertEquals(4, RenderOptions.distanceFrom(" 4 "));
    }
}
