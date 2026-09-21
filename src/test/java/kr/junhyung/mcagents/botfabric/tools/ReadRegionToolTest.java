package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The run lengths and the palette they point into, which are where a region can answer wrongly
 * without looking wrong. A run carried across a position that was left out -- a chunk the client
 * has not got, or air the caller asked to drop -- reports a solid span that is not there, and a run
 * never closed drops the blocks it had counted. Neither shows up in the totals, so neither is
 * visible in a reading of the answer.
 */
class ReadRegionToolTest {

    private static String encoded(ReadRegionTool.Runs runs) {
        StringBuilder spelled = new StringBuilder();
        for (JsonElement element : runs.json()) {
            JsonObject run = element.getAsJsonObject();
            spelled.append(spelled.isEmpty() ? "" : " ")
                    .append(run.get("block").getAsInt())
                    .append("x")
                    .append(run.get("count").getAsInt());
        }
        return spelled.toString();
    }

    @Test
    void neighboursOfOneKindCountAsOneRunAndAChangeStartsAnother() {
        ReadRegionTool.Runs runs = new ReadRegionTool.Runs();
        runs.add(0);
        runs.add(0);
        runs.add(1);
        runs.add(0);
        runs.close();

        assertEquals("0x2 1x1 0x1", encoded(runs));
    }

    @Test
    void aGapKeepsTwoStretchesOfOneKindApart() {
        ReadRegionTool.Runs runs = new ReadRegionTool.Runs();
        runs.add(0);
        runs.close();
        runs.add(0);
        runs.close();

        assertEquals("0x1 0x1", encoded(runs));
    }

    @Test
    void aStretchOfGapsAddsNoEmptyRuns() {
        ReadRegionTool.Runs runs = new ReadRegionTool.Runs();
        runs.add(2);
        runs.close();
        runs.close();
        runs.close();

        assertEquals("2x1", encoded(runs));
    }

    /**
     * The palette and the runs wired together the way {@code read} wires them, and then read back
     * the other way. An index is only a number: the sole thing saying which block it means is
     * where that block sits in the palette, so an index off by one is the worst thing this tool
     * can do -- the runs still validate, the totals still add up, and the build that is drawn from
     * them is made of the wrong blocks. Spelling the answer back out is what catches it, because
     * nothing about the answer itself looks wrong.
     */
    @Test
    void theRunsSpellBackOutTheBlocksTheyWereBuiltFrom() {
        List<String> wall = List.of("stone", "stone", "dirt", "dirt", "stone", "glass", "stone");

        Map<String, Integer> palette = new LinkedHashMap<>();
        ReadRegionTool.Runs runs = new ReadRegionTool.Runs();
        for (String block : wall) {
            runs.add(ReadRegionTool.index(palette, block));
        }
        runs.close();

        assertEquals(List.of("stone", "dirt", "glass"), List.copyOf(palette.keySet()));
        assertEquals("0x2 1x2 0x1 2x1 0x1", encoded(runs));
        assertEquals(wall, spelledOut(runs, palette));
    }

    /** The answer read the way a caller reads it: each run's index looked up in the palette. */
    private static List<String> spelledOut(ReadRegionTool.Runs runs, Map<String, Integer> palette) {
        List<String> names = List.copyOf(palette.keySet());
        List<String> blocks = new ArrayList<>();
        for (JsonElement element : runs.json()) {
            JsonObject run = element.getAsJsonObject();
            for (int drawn = 0; drawn < run.get("count").getAsInt(); drawn++) {
                blocks.add(names.get(run.get("block").getAsInt()));
            }
        }
        return blocks;
    }
}
