package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import kr.junhyung.mcagents.botfabric.text.Readings;
import org.junit.jupiter.api.Test;

/**
 * Which feed lines a waitFor may take. A reply the server sends while the step before is still
 * settling has to count, and a line one waitFor took must not satisfy the next one too -- the
 * conversation in the fixture repeats its prompt, and two waitFors on it must wait for two.
 */
class SequenceTaskTest {

    private static final Watch FINE_DAY = Watch.of("actionBar", "Fine day");

    @Test
    void aLineThatArrivedDuringTheStepBeforeCounts() {
        SequenceTask.Lines lines = new SequenceTask.Lines();
        int click = lines.begin();
        lines.add("actionBar", Readings.plain("[Probe Farmer] Fine day for it."));
        int waitFor = lines.begin();

        assertEquals(0, click);
        assertEquals(0, waitFor);
        assertEquals("[Probe Farmer] Fine day for it.", lines.take(waitFor, FINE_DAY));
    }

    @Test
    void aLineFromBeforeTheStepBeforeDoesNotCount() {
        SequenceTask.Lines lines = new SequenceTask.Lines();
        lines.begin();
        lines.add("actionBar", Readings.plain("Fine day, said earlier"));
        lines.begin();
        int waitFor = lines.begin();

        assertNull(lines.take(waitFor, FINE_DAY));
    }

    @Test
    void aLineTakenOnceIsNotTakenAgain() {
        SequenceTask.Lines lines = new SequenceTask.Lines();
        int first = lines.begin();
        lines.add("actionBar", Readings.plain("Fine day"));
        assertEquals("Fine day", lines.take(first, FINE_DAY));

        int second = lines.begin();
        assertNull(lines.take(second, FINE_DAY));

        lines.add("actionBar", Readings.plain("Fine day again"));
        assertEquals("Fine day again", lines.take(second, FINE_DAY));
    }

    @Test
    void aLineOnAnotherFeedDoesNotCount() {
        SequenceTask.Lines lines = new SequenceTask.Lines();
        int waitFor = lines.begin();
        lines.add("title", Readings.plain("Fine day"));

        assertNull(lines.take(waitFor, FINE_DAY));
    }
}
