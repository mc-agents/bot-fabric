package kr.junhyung.mcagents.botfabric.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import kr.junhyung.mcagents.botfabric.tool.ToolException;
import org.junit.jupiter.api.Test;

/**
 * A step arrives with all thirteen fields filled in, so its kind is read off which naming field is
 * not null and nothing else. What is asserted is that reading, the refusals a caller gets before
 * anything is sent, and the words a step is named by in the answer -- which the other kind of bot
 * spells the same, so a renderer sentence reads the same whichever bot ran it.
 */
class StepTest {

    /** As the Normaliser sends it: every field present, the ones outside the kind at their defaults. */
    private static JsonObject step(String given) {
        JsonObject step = JsonParser.parseString("""
                {"press": null, "slot": null, "holdTicks": 1, "useItem": null, "click": null, "button": "left",
                 "shift": false, "mode": "click", "hotbar": null, "command": null, "wait": null, "waitFor": null,
                 "feed": "actionBar"}
                """).getAsJsonObject();
        JsonParser.parseString(given).getAsJsonObject().entrySet()
                .forEach(entry -> step.add(entry.getKey(), entry.getValue()));
        return step;
    }

    private static List<Step> parse(String... given) {
        JsonArray steps = new JsonArray();
        for (String one : given) {
            steps.add(step(one));
        }
        return Step.parse(steps);
    }

    private static Step one(String given) {
        return parse(given).get(0);
    }

    @Test
    void aClickWithTheDefaultsIsAPlainLeftClickInClickSlotsOwnNames() {
        Step.Click click = assertInstanceOf(Step.Click.class, one("{\"click\": 13}"));

        assertEquals("click slot 13", click.asked());
        JsonObject args = click.args();
        assertEquals(13, args.get("slot").getAsInt());
        assertEquals(false, args.get("outside").getAsBoolean());
        assertEquals("left", args.get("button").getAsString());
        assertEquals(false, args.get("shift").getAsBoolean());
        assertEquals("click", args.get("mode").getAsString());
        assertTrue(args.get("hotbar").isJsonNull());
    }

    @Test
    void eachKindIsNamedTheWayTheAnswerNamesIt() {
        assertEquals("press jump", one("{\"press\": \"jump\"}").asked());
        assertEquals("press hotbar 3", one("{\"press\": \"hotbar\", \"slot\": 3}").asked());
        assertEquals("press use for 40 ticks", one("{\"press\": \"use\", \"holdTicks\": 40}").asked());
        assertEquals("use item in main hand", one("{\"useItem\": \"main-hand\"}").asked());
        assertEquals("use item in off-hand", one("{\"useItem\": \"off-hand\"}").asked());
        assertEquals("use item in main hand for 40 ticks", one("{\"useItem\": \"main-hand\", \"holdTicks\": 40}").asked());
        assertEquals("use item in off-hand for 2 ticks", one("{\"useItem\": \"off-hand\", \"holdTicks\": 2}").asked());
        assertEquals("click slot 13 with the right button", one("{\"click\": 13, \"button\": \"right\"}").asked());
        assertEquals("click slot 5 with shift", one("{\"click\": 5, \"shift\": true}").asked());
        assertEquals("swap-hotbar slot 13 with hotbar 2",
                one("{\"click\": 13, \"mode\": \"swap-hotbar\", \"hotbar\": 2}").asked());
        assertEquals("throw-one slot 5", one("{\"click\": 5, \"mode\": \"throw-one\"}").asked());
        assertEquals("wait 20 ticks", one("{\"wait\": 20}").asked());
        assertEquals("wait 1 tick", one("{\"wait\": 1}").asked());
        assertEquals("wait for /Fine day/ on actionBar", one("{\"waitFor\": \"Fine day\"}").asked());
        assertEquals("wait for /What will/ on title", one("{\"waitFor\": \"What will\", \"feed\": \"title\"}").asked());
    }

    @Test
    void aCommandCarriesExactlyOneSlashHoweverItWasGiven() {
        assertEquals("command /fixture pling BOT", one("{\"command\": \"fixture pling BOT\"}").asked());
        assertEquals("command /fixture pling BOT", one("{\"command\": \"/fixture pling BOT\"}").asked());
        Step.Command command = assertInstanceOf(Step.Command.class, one("{\"command\": \"fixture pling BOT\"}"));
        assertEquals("/fixture pling BOT", command.text());
    }

    /** The slot only means something for hotbar; given with another key it is dropped rather than sent. */
    @Test
    void aSlotOutsideHotbarIsDropped() {
        Step.Press press = assertInstanceOf(Step.Press.class, one("{\"press\": \"jump\", \"slot\": 3}"));

        assertNull(press.slot());
    }

    @Test
    void aStepNamingNoKindOrTwoIsRefusedByItsNumber() {
        ToolException none = assertThrows(ToolException.class,
                () -> parse("{\"click\": 13}", "{}", "{\"wait\": 5}"));
        assertEquals("BAD_STEP", none.code());
        assertEquals("step 2 names none of press, click, useItem, command, wait or waitFor", none.getMessage());

        ToolException both = assertThrows(ToolException.class,
                () -> parse("{\"click\": 13}", "{\"wait\": 5}", "{\"press\": \"jump\", \"click\": 1}"));
        assertEquals("BAD_STEP", both.code());
        assertEquals("step 3 names both press and click", both.getMessage());

        /* The two are named in the order the kinds are listed, whichever the step wrote first. */
        ToolException useAndClick = assertThrows(ToolException.class,
                () -> parse("{\"useItem\": \"main-hand\", \"click\": 13}"));
        assertEquals("BAD_STEP", useAndClick.code());
        assertEquals("step 1 names both click and useItem", useAndClick.getMessage());
    }

    @Test
    void hotbarWithoutASlotIsRefusedByItsNumber() {
        ToolException refused = assertThrows(ToolException.class,
                () -> parse("{\"wait\": 5}", "{\"press\": \"hotbar\"}"));

        assertEquals("NO_SLOT", refused.code());
        assertEquals("step 2: hotbar needs slot", refused.getMessage());
    }

    /** A field at fault is named after its step whichever kind it shapes, as the other kind of bot words it. */
    @Test
    void aFieldAtFaultIsRefusedByItsStepsNumber() {
        ToolException key = assertThrows(ToolException.class,
                () -> parse("{\"wait\": 5}", "{\"press\": \"fly\"}"));
        assertEquals("BAD_ARGS", key.code());
        assertEquals("step 2: unknown key fly", key.getMessage());

        ToolException slot = assertThrows(ToolException.class, () -> parse("{\"click\": \"thirteen\"}"));
        assertEquals("BAD_ARGS", slot.code());
        assertEquals("step 1: expected an integer for click", slot.getMessage());

        ToolException hand = assertThrows(ToolException.class, () -> parse("{\"useItem\": \"both\"}"));
        assertEquals("BAD_ARGS", hand.code());
        assertEquals("step 1: unknown hand both", hand.getMessage());
    }

    @Test
    void aBrokenPatternIsRefusedWithTheSourceQuotedBackByItsNumber() {
        ToolException refused = assertThrows(ToolException.class,
                () -> parse("{\"wait\": 5}", "{\"waitFor\": \"Shop([\"}"));

        assertEquals("BAD_PATTERN", refused.code());
        assertTrue(refused.getMessage().startsWith("step 2: \"Shop([\" is not a valid regular expression: "),
                refused.getMessage());
    }

    @Test
    void aStepThatIsNotAnObjectIsRefusedByItsNumber() {
        JsonArray steps = new JsonArray();
        steps.add(step("{\"wait\": 5}"));
        steps.add(7);

        ToolException refused = assertThrows(ToolException.class, () -> Step.parse(steps));
        assertEquals("BAD_ARGS", refused.code());
        assertEquals("step 2 is not an object", refused.getMessage());
    }

    /**
     * What TOO_LONG adds up: a press is down for holdTicks and up for one, a useItem in use for
     * holdTicks and let go of for one, a click one tick, a wait its own, the rest none.
     */
    @Test
    void theLeastASequenceTakesIsAddedUpFromItsSteps() {
        List<Step> steps = parse("{\"press\": \"use\", \"holdTicks\": 40}", "{\"wait\": 20}", "{\"click\": 13}",
                "{\"useItem\": \"off-hand\", \"holdTicks\": 3}", "{\"useItem\": \"main-hand\"}",
                "{\"command\": \"fixture pling BOT\"}", "{\"waitFor\": \"Fine day\"}");

        assertEquals((41 + 20 + 1 + 4 + 2) * 50L, SequenceTask.atLeastMs(steps));
    }
}
