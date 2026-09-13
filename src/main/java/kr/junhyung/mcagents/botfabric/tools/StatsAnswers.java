package kr.junhyung.mcagents.botfabric.tools;

/**
 * Which statistics answer belongs to which request.
 *
 * <p>The client already holds statistics from the last time it asked, so reading before the answer
 * lands gives the old numbers with nothing to say they are old. Nor does an answer carry anything
 * to match it to a request: the server sends only what changed since it last sent, and an answer
 * with nothing in it is still an answer. What does hold is order. The server answers every request,
 * one answer each, in the order they were sent, so a request is answered by the answer whose number
 * is the count of answers already in plus the requests still waiting, its own included.
 *
 * <p>Counted per connection. A request sent to a backend that is then left behind is never
 * answered, and counting across it would leave every later read waiting for one answer too many.
 *
 * <p>Client thread only, like the packet handler that calls {@link #answered} and the tasks that ask.
 */
public final class StatsAnswers {

    public static final StatsAnswers CLIENT = new StatsAnswers();

    private Object connection;
    private int answered;
    private int waiting;

    StatsAnswers() {
    }

    /** Sent a request on this connection. The number is what {@link #arrived} waits for. */
    int asked(Object on) {
        if (on != connection) {
            connection = on;
            answered = 0;
            waiting = 0;
        }
        waiting++;
        return answered + waiting;
    }

    public void answered(Object on) {
        if (on == connection) {
            answered++;
        }
    }

    boolean arrived(Object on, int awaited) {
        return on == connection && answered >= awaited;
    }

    /**
     * A request stopped waiting, answered or not. One that gave up has to stop counting too, or the
     * answer it never got would be waited for by every request after it.
     */
    void settled(Object on) {
        if (on == connection && waiting > 0) {
            waiting--;
        }
    }
}
