package kr.junhyung.mcagents.botfabric.tools;

import java.util.Locale;

/**
 * Numbers as they appear in a sentence.
 *
 * <p>The other kind of bot builds the same sentences in JavaScript, where 8.0 prints as "8" and a
 * distance is {@code toFixed(1)}. A radius that reads "within 8.0 blocks" on one bot and "within 8
 * blocks" on the other is a difference an agent can see, and the comparison suite reports it.
 */
final class Numbers {

    private Numbers() {
    }

    static String plain(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e21) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
