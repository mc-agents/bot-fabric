package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The offset is the label minus the feet, and getting its sign backwards reads a label as belonging
 * to whatever stands on top of it rather than to the NPC it floats over.
 */
class NameplatesTest {

    @Test
    void feetUpToThreeBlocksBelowAndOneToTheSideAreUnder() {
        assertTrue(Nameplates.under(0, 2.4, 0));
        assertTrue(Nameplates.under(0, 3.0, 0));
        assertTrue(Nameplates.under(0.6, 0, 0.8));
    }

    @Test
    void feetAboveTheLabelOrFurtherOffAreNot() {
        assertFalse(Nameplates.under(0, -0.1, 0));
        assertFalse(Nameplates.under(0, 3.1, 0));
        assertFalse(Nameplates.under(0.8, 2, 0.8));
    }
}
