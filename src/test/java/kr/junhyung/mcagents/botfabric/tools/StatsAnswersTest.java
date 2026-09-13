package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * An answer carries nothing that says which request it answers, so a read that took someone
 * else's answer reads numbers from before its own request, and nothing about them looks wrong.
 */
class StatsAnswersTest {

    private final Object connection = new Object();

    @Test
    void aSecondRequestIsNotAnsweredByTheFirstAnswer() {
        StatsAnswers answers = new StatsAnswers();
        int first = answers.asked(connection);
        int second = answers.asked(connection);

        answers.answered(connection);

        assertTrue(answers.arrived(connection, first));
        assertFalse(answers.arrived(connection, second));

        answers.answered(connection);

        assertTrue(answers.arrived(connection, second));
    }

    @Test
    void anAnswerFromBeforeTheRequestDoesNotCount() {
        StatsAnswers answers = new StatsAnswers();
        int earlier = answers.asked(connection);
        answers.answered(connection);
        answers.settled(connection);

        int later = answers.asked(connection);

        assertTrue(answers.arrived(connection, earlier));
        assertFalse(answers.arrived(connection, later));
    }

    /** Otherwise the one answer that never came is waited for by every read after it. */
    @Test
    void aRequestThatGaveUpIsNotWaitedForByTheNext() {
        StatsAnswers answers = new StatsAnswers();
        answers.asked(connection);
        answers.settled(connection);

        int next = answers.asked(connection);
        answers.answered(connection);

        assertTrue(answers.arrived(connection, next));
    }

    /** A request left on a backend the proxy moved away from is never answered. */
    @Test
    void aNewConnectionStartsCountingAgain() {
        StatsAnswers answers = new StatsAnswers();
        Object old = new Object();
        answers.asked(old);

        int fresh = answers.asked(connection);
        answers.answered(old);

        assertFalse(answers.arrived(connection, fresh));

        answers.answered(connection);

        assertTrue(answers.arrived(connection, fresh));
        assertFalse(answers.arrived(old, fresh));
    }
}
